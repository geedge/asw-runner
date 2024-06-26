package net.geedge.executor;

import cn.hutool.log.Log;
import lombok.Data;
import net.geedge.util.RunnerContext;
import net.geedge.util.RunnerYml.ExecutorConfig;

@Data
public class Executor implements Runnable{
	private ExecutorConfig executorConfig;
	private JobInfo jobInfo;
	Log log = Log.get();
	
	@Override
	public void run() {
		Thread.currentThread().setName("job-excutor-"+jobInfo.getId());
		log.info("job executor start");
		RunnerContext.addActiveExecutors(this);
		try {
			log.info("job executor run job start");
			this.runJob();
			log.info("job executor run job end");
		} finally {
			RunnerContext.removeActiveExecutors(this);
			log.info("job executor run jobend");
		}
	}
	
	/**
	 * 运行playbook
	 * @param path playbook文件路径
	 * @param config executor配置
	 */
	void runJob() {
		
	};


}
