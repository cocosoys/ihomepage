package soys.ihomepages.log;

public class LogKit extends soys.soyshttpovermc.log.LogKit {

    public LogKit(String prefix, Class<?> sourceClass) {
        super(prefix, sourceClass);
    }

    // ========= Lombok 两个重载工厂 =========
    public static LogKit getLogger(Class<?> clazz) {
        return new LogKit("[iHomePage]", clazz);
    }

    public static LogKit getLogger(Class<?> clazz, String topic) {
        return new LogKit("[" + topic + "]", clazz);
    }
}
