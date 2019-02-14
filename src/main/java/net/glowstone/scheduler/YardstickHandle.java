package net.glowstone.scheduler;

public abstract class YardstickHandle {
	private static final boolean enabled = determine_enabled();

	private static boolean determine_enabled() {
		String got = System.getProperty("yardstick.gateway.enabled", "false");
		if (got.equals("true")) {
			System.out.println("!! Gateway enabled!");
			return true;
		} else {
			System.out.println("!! Gateway disabled!");
			return false;
		}
	}

	public static void start(String first, String second) {
		if (enabled) {
			YSCollector.start(first, second);
		} 
	}
	public static void stop(String str) {
		if (enabled) {
            YSCollector.stop(str);
		}
	}
}