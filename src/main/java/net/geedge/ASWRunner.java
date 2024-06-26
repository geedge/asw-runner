package net.geedge;

import java.io.File;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import cn.hutool.core.lang.Dict;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.log.Log;
import cn.hutool.setting.yaml.YamlUtil;
import net.geedge.executor.Executor;
import net.geedge.executor.JobInfo;
import net.geedge.util.RunnerContext;
import net.geedge.util.RunnerContext.Parameters;
import net.geedge.util.RunnerYml;
import net.geedge.util.RunnerYml.ExecutorConfig;
import net.geedge.util.T;

/**
 * 程序主要包括两个线程 </br>
 * 1、心跳线程 </br>
 * 
 * 2、任务请求线程 </br>
 * 
 */
public class ASWRunner {
	private static final Log log = Log.get();

	public static void main(String[] args) {
		// 解析命令行参数
		Parameters param = parseArgs(args);
		RunnerContext.setParameters(param);
		// 加载配置文件并校验
		loadConfig();
		// 注册登录，阻塞直到注册成功
		register();
		// 启动心跳线程
		startHeartBeatThread();

	}

	private static Parameters parseArgs(String[] args) {
		Option configOption = Option.builder("c").longOpt("config_path").hasArg(true).desc("config file path")
				.required(false).build();
		Option intervalOption = Option.builder("t").longOpt("heartbeat_interval").hasArg(true)
				.desc("heart beat interval milliseconds").required(false).build();
		// 解析命令行参数
		Options options = new Options();
		options.addOption(configOption);
		options.addOption(intervalOption);
		CommandLine cli = null;
		CommandLineParser cliParser = new DefaultParser();
		HelpFormatter helpFormatter = new HelpFormatter();

		try {
			cli = cliParser.parse(options, args);
		} catch (Exception e) {
			// 解析失败是用 HelpFormatter 打印 帮助信息
			helpFormatter.printHelp("AppSketch Works Runners", options);
			log.error(e, "args parse error");
			System.exit(1);
		}
		// 配置文件路径
		String configPath = T.StrUtil.isNotBlank(cli.getOptionValue(configOption)) ? cli.getOptionValue(configOption)
				: RunnerContext.DEFAULT_CONFIG_PATH;
		// 心跳周期间隔时间
		Integer heartBeatInterval = T.StrUtil.isNotBlank(cli.getOptionValue(intervalOption))
				? T.NumberUtil.parseInt(cli.getOptionValue(intervalOption))
				: RunnerContext.DEFAULT_HEARTBEAT_INTERVAL;
		log.debug("config path: {}", configPath);
		log.debug("heartbeat interval: {}ms", heartBeatInterval);
		return new Parameters(configPath, heartBeatInterval);
	}

	/**
	 * 加载配置文件
	 */
	private static void loadConfig() {
		try {
			String configPath = RunnerContext.getParameters().configPath();
			if(!T.FileUtil.isAbsolutePath(configPath)) {
				// 获取当前JAR包的路径
	            String jarPath = ASWRunner.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	            // 创建File对象并获取目录
	            configPath = T.FileUtil.file(T.FileUtil.file(jarPath).getParentFile(), configPath).getAbsolutePath();
			}
			log.info("load config file, absolute path : {}", configPath);
			Dict dict = YamlUtil.loadByPath(configPath);
			RunnerYml runnerYml = T.BeanUtil.copyProperties(dict, RunnerYml.class);
			RunnerContext.setRunnerYml(runnerYml);
			String registerUrl = runnerYml.getRegister().getUrl();
			if (T.StrUtil.isBlank(registerUrl)) {
				throw new RuntimeException("register url is blank");
			}
			if (T.CollUtil.isEmpty(runnerYml.getExecutors())) {
				throw new RuntimeException("no executor");
			}
			log.debug("config content: {}", T.JSONUtil.toJsonStr(runnerYml));
		} catch (Exception e) {
			log.error(e, "load config error");
			System.exit(2);
		}
	}

	/**
	 * 注册登录
	 * 
	 * @param url
	 * @param token
	 * @return
	 */
	private static void register() {
		while (RunnerContext.getRunFlag()) {
			try {
				String url = RunnerContext.getRegisterPath();
				String token = RunnerContext.getRegisterToken();
				log.info("start register,url:{}", url);
				HttpRequest request = T.HttpUtil.createPost(url);
				request.addHeaders(T.MapUtil.of("Authorization", token));
				HttpResponse response = request.execute();
				int status = response.getStatus();
				log.info("register status: {}", status);
				if (status == 200) {
					log.info("register success");
					break;
				}
			} catch (Exception e) {
				log.error(e, "register error");
			}
			T.ThreadUtil.sleep(5000);
		}
	}

	/**
	 * 心跳线程
	 */
	private static void startHeartBeatThread() {
		T.ThreadUtil.execAsync(() -> {
			Thread.currentThread().setName("heartbeat-thread");
			while (RunnerContext.getRunFlag()) {
				try {
					// 获取当前运行状态
					Map<String, Integer> freeExecutorNum = RunnerContext.getFreeExecutorNum();
					if (log.isDebugEnabled()) {
						log.debug("heart beat body: {}", T.JSONUtil.toJsonStr(freeExecutorNum));
					}
					// 发送http请求
					HttpRequest request = T.HttpUtil.createPost(RunnerContext.getheartbeatPath());
					request.addHeaders(T.MapUtil.of("Authorization", RunnerContext.getRegisterToken()));
					request.body(T.JSONUtil.toJsonStr(freeExecutorNum));
					HttpResponse response = request.execute();
					// 解析http响应，创建任务
					int status = response.getStatus();
					log.info("heart beat status: {}", status);
					String body = response.body();
					log.debug("heart beat response body: {}", body);
					if (status == 200 && T.StrUtil.isNotBlank(body)) {
						JobInfo jobInfo = T.JSONUtil.toBean(body, JobInfo.class);
						String platform = jobInfo.getPkg().getPlatform();
						log.info("jobinfo id: {}, packageId: {}, platform: {}", jobInfo.getId(),
								jobInfo.getPkg().getId(), platform);
						ExecutorConfig executorConfig = RunnerContext.getFreeExecutorByPlatform(platform);
						if (executorConfig == null) {
							throw new RuntimeException(T.StrUtil.format("no free executor, platform: {} ", platform));
						}
						Executor exec = new Executor();
						exec.setJobInfo(jobInfo);
						exec.setExecutorConfig(executorConfig);
						T.ThreadUtil.execute(exec);
					}
				} catch (Exception e) {
					log.error(e, "heart beat error");
				}
				/*
				 * 心跳线程下次继续
				 */
				T.ThreadUtil.sleep(RunnerContext.getHeartbeatInterval());
			}
		});
	}

}
