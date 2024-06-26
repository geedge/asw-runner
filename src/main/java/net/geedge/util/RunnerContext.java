package net.geedge.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import net.geedge.executor.Executor;
import net.geedge.util.RunnerYml.ExecutorConfig;

public class RunnerContext {
	/**
	 * 默认配置文件路径
	 */
	public static final String DEFAULT_CONFIG_PATH = "./runner.yml";
	/**
	 * 默认心跳间隔 5s
	 */
	public static final Integer DEFAULT_HEARTBEAT_INTERVAL = 5000;
	public static final String REGISTER_PATH = "/register";
	/**
	 * 心跳接口路径
	 */
	private static final String HEARTBEAT_PATH = "/heartbeat";
	/**
	 * 程序运行标识
	 */
	private volatile static boolean runFlag = true;

	/**
	 * 命令行参数
	 */
	public record Parameters(String configPath, Integer heartbeatInterval) {
	};

	/**
	 * 保存命令行参数
	 */
	private volatile static Parameters parameters;
	/**
	 * 保存配置文件参数
	 */
	private volatile static RunnerYml runnerYml;
	/**
	 * 记录当前正在运行的 executor
	 */
	private volatile static List<Executor> activeExecutors = T.ListUtil.toCopyOnWriteArrayList(null);

	
	public static boolean getRunFlag() {
		return runFlag;
	}

	public static String getRegisterPath() {
		return REGISTER_PATH;
	}

	public static String getheartbeatPath() {
		return HEARTBEAT_PATH;
	}

	public static String getRegisterToken() {
		return HEARTBEAT_PATH;
	}

	public static void setParameters(Parameters param) {
		parameters = param;
	}

	public static Parameters getParameters() {
		return parameters;
	}

	public static Integer getHeartbeatInterval() {
		return parameters.heartbeatInterval();
	}

	public static String getConfigPath() {
		return parameters.configPath();
	}

	public static RunnerYml getRunnerYml() {
		return runnerYml;
	}

	public static void setRunnerYml(RunnerYml yml) {
		runnerYml = yml;
	}

	/**
	 * 获取空闲 executor 数量
	 * 
	 * platform:num
	 * 
	 * @return
	 */
	public static List<ExecutorConfig> getFreeExecutor() {
		List<ExecutorConfig> result = T.ListUtil.list(false);
		// 记录每个平台可以提供几个运行实例
		runnerYml.getExecutors().forEach(e -> {
			result.add(e);
		});
		// 减去现有的运行实例
		activeExecutors.forEach(ae -> {
			ExecutorConfig config = ae.getExecutorConfig();
			result.forEach(e -> {
				if (e.getType().equals(config.getType()) && e.getPlatform().equals(config.getPlatform())) {
					e.setConcurrent(e.getConcurrent() - 1);
				}
			});
		});
		return result;
	}

	public static ExecutorConfig getFreeExecutorByPlatform(String platform) {
		List<ExecutorConfig> freeExecutor = getFreeExecutor();
		for (ExecutorConfig e : freeExecutor) {
			if(e.getPlatform().equals(platform)) {
				return e;
			}
		}
		return null;
	}
	
	public static Map<String, Integer> getFreeExecutorNum() {
		List<ExecutorConfig> list = getFreeExecutor();
		Map<String,Integer> result = T.MapUtil.newHashMap();
		list.forEach(c -> {
			Integer num = result.getOrDefault(c.getPlatform(), 0);
			result.put(c.getPlatform(), num+c.getConcurrent());
		});
		return result;
	}
	
	public static void addActiveExecutors(Executor e) {
		activeExecutors.add(e);
	}
	
	public static void removeActiveExecutors(Executor e) {
		Iterator<Executor> ite = activeExecutors.iterator();
		while(ite.hasNext()) {
			Executor next = ite.next();
			if(next.getJobInfo().getId().equals(e.getJobInfo().getId())) {
				ite.remove();
			}
		}
	}
}
