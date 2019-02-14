package net.glowstone.scheduler;

public abstract class YardstickHandle {
	private static final boolean enabled = false;

	public static void start(String first, String second) {
		if (enabled) {
			com.atlarge.yscollector.YSCollector.start(first, second); // YSCollector
		} 
	}
	public static void stop(String str) {
		if (enabled) {
            com.atlarge.yscollector.YSCollector.stop(str);
		}
	}
}