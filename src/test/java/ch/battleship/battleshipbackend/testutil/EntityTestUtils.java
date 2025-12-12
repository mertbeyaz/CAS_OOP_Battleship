package ch.battleship.battleshipbackend.testutil;

import java.lang.reflect.Field;
import java.util.UUID;

public final class EntityTestUtils {

    private EntityTestUtils() {
        // utility class
    }

    public static void setId(Object entity, UUID id) {
        Class<?> current = entity.getClass();

        while (current != null) {
            try {
                Field idField = current.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, id);
                return;
            } catch (NoSuchFieldException e) {
                // weiter nach oben in der Vererbungshierarchie
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to set id via reflection", e);
            }
        }

        throw new IllegalArgumentException("No field 'id' found on class " + entity.getClass());
    }
}
