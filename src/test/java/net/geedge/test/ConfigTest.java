package net.geedge.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import net.geedge.util.RunnerYml;
import net.geedge.util.T;

public class ConfigTest {

	@Test
	public void loadConfig() {
		Dict dict = YamlUtil.load(T.FileUtil.getUtf8Reader("./runner.yml"));
		RunnerYml runnerYml = T.BeanUtil.copyProperties(dict, RunnerYml.class);
		String url = runnerYml.getRegister().getUrl();
		assertEquals(url, "http://asw.geedge.net");
		System.out.println(T.JSONUtil.toJsonStr(runnerYml));
	}
	
	@Test
	public void testClassPath() {
		String classPath = T.ClassUtil.getClassPath();
		System.out.println(classPath);
	}
	
}
