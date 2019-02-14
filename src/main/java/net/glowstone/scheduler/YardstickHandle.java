package net.glowstone.scheduler;

public abstract class YardstickHandle {
	private static final boolean enabled = determine_enabled();

	private static boolean determine_enabled() {
		if System.getProperty("yardstick.gateway.enabled", "false") == "true" {
			return true;
		} else {
			return false;
		}
	}

	public static void start(String first, String second) {
		if (enabled) {
			com.atlarge.yscollector.YSCollector.start(first, second);
		} 
	}
	public static void stop(String str) {
		if (enabled) {
            com.atlarge.yscollector.YSCollector.stop(str);
		}
	}
}