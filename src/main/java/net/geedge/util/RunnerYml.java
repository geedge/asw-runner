package net.geedge.util;

import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
public class RunnerYml {

	private Register register;
	private List<ExecutorConfig> executors;

	@Data
	public class Register {
		String url;
		String token;
	}

	@Data
	public class ExecutorConfig {
		String type;
		String platform;
		Integer concurrent;
		String image;
	}

}
