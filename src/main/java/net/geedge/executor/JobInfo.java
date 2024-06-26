package net.geedge.executor;

import java.util.List;

import lombok.Data;

@Data
public class JobInfo {
	private String id;
	private Playbook playbook;
	private Package pkg;
	private List<Signature> signatures;

	@Data
	public class Package {
		String id;
		String platform;
		String version;
		String identifier;
	}

	@Data
	public class Playbook {
		String id;
		String name;
		String tags;
	}

	@Data
	public class Signature {
		String id;
		String name;
		String conditions;
	}
}
