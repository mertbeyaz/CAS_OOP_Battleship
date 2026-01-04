package ch.battleship.battleshipbackend.testutil;

import java.lang.reflect.Field;
import java.util.UUID;

/**
 * Test utility helpers for manipulating domain entities in unit tests.
 *
 * <p><b>Why this exists:</b>
 * In unit tests we often create domain objects directly (without JPA/Hibernate).
 * Those objects normally receive their {@code id} from the persistence layer.
 * To simulate a persisted state (e.g., for equality checks, ownership checks, controller DTOs,
 * or "current turn player" logic), tests may need to assign a deterministic UUID.</p>
 *
 * <p><b>How it works:</b>
 * Uses Java reflection to locate a field named {@code id} on the given entity instance.
 * The lookup walks up the class inheritance hierarchy (superclasses), so it also works if
 * {@code id} is declared on a mapped superclass (e.g., {@code BaseEntity}). Once found,
 * the field is set to accessible and assigned the given UUID.</p>
 *
 * <p><b>Usage example:</b>
 * <pre>{@code
 * Player p = new Player("Alice");
 * UUID playerId = UUID.fromString("20000000-0000-0000-0000-000000000001");
 * EntityTestUtils.setId(p, playerId);
 * }</pre>
 *
 * <p><b>Important notes:</b>
 * <ul>
 *   <li>This is intended for test code only. Do not use in production code.</li>
 *   <li>It assumes the identifier field is called exactly {@code id}.</li>
 *   <li>If no {@code id} field can be found anywhere in the class hierarchy, an
 *       {@link IllegalArgumentException} is thrown to fail fast (test setup issue).</li>
 * </ul>
 */
public final class EntityTestUtils {

    /**
     * Private constructor to prevent instantiation (utility class pattern).
     */
    private EntityTestUtils() {
        // utility class
    }

    /**
     * Sets the {@code id} field of the given entity instance to the provided UUID.
     *
     * <p>The method searches for a field named {@code id} on the entity's class and,
     * if not found, continues searching the superclass chain until it reaches {@code Object}.</p>
     *
     * @param entity the domain object whose {@code id} should be set (must not be {@code null})
     * @param id     the UUID to assign to the {@code id} field (may be {@code null} if tests want to simulate "no id")
     *
     * @throws IllegalArgumentException if no field named {@code id} exists in the class hierarchy
     * @throws RuntimeException if the field exists but cannot be accessed (should not happen in tests)
     */
    public static void setId(Object entity, UUID id) {
        Class<?> current = entity.getClass();

        while (current != null) {
            try {
                Field idField = current.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, id);
                return;
            } catch (NoSuchFieldException e) {
                // Continue searching up the inheritance hierarchy
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                // Should be unlikely after setAccessible(true), but fail loudly if it happens
                throw new RuntimeException("Failed to set id via reflection", e);
            }
        }

        throw new IllegalArgumentException("No field 'id' found on class " + entity.getClass());
    }
}
