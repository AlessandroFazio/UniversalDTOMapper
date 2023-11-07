package github.alessandro.fazio;

import github.alessandro.fazio.annotations.Dto;
import github.alessandro.fazio.annotations.MapManager;
import github.alessandro.fazio.annotations.MapReference;
import github.alessandro.fazio.annotations.Mapper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * The DTOMapper class provides a utility for mapping objects to Data Transfer Objects (DTOs).
 * This utility works with nested DTOs and Arrays of DTOs, but not with Collections of DTOs.
 *
 * @author Alessandro Fazio
 * @version 1.0
 */
public class UniversalDTOMapper {
    /**
     * Maps a source object to a Data Transfer Object (DTO) using reflection.
     *
     * @param sourceObj The source object to map to the DTO.
     * @param DTOclass The class of the DTO to which the object will be mapped.
     * @param <T> The type of the source object.
     * @param <R> The type of the DTO.
     * @return The mapped DTO object.
     * @throws IllegalAccessException If an error occurs during mapping.
     * @throws NoSuchMethodException If a required constructor is not found.
     * @throws InvocationTargetException If an error occurs during method invocation.
     * @throws InstantiationException If an error occurs during object instantiation.
     */
    public static <T, R> R map(@NotNull T sourceObj, @NotNull Class<R> DTOclass)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, InstantiationException {

        R dtoObj = DTOclass.getConstructor().newInstance();
        Map<String, Field> dtoNameToField = createDTONameToField(DTOclass);

        for (Field objField : sourceObj.getClass().getDeclaredFields()) {
            if (objField.isAnnotationPresent(Mapper.class)) {
                mapFieldWithAnnotation(objField, sourceObj, dtoObj, DTOclass);
            } else if (dtoNameToField.containsKey(objField.getName())) {
                mapFieldWithoutAnnotation(objField, sourceObj, dtoObj, dtoNameToField);
            }
        }
        return dtoObj;
    }

    /**
     * Maps a source object to a Data Transfer Object (DTO) using reflection.
     *
     * @param sourceObj The source object to map to the DTO.
     * @return The mapped DTO object.
     * @throws InvocationTargetException If an error occurs during method invocation.
     * @throws IllegalAccessException If an error occurs during mapping.
     * @throws NoSuchMethodException If a required constructor is not found.
     * @throws InstantiationException If an error occurs during object instantiation.
     * @throws IllegalArgumentException If the source object's class or MapManager annotation is invalid.
     */
    public static <T> Object map(@NotNull T sourceObj)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        if (!sourceObj.getClass().isAnnotationPresent(MapManager.class)) {
            throw new IllegalArgumentException("The sourceObj Class is not annotated " +
                    "with MapManager Annotation");
        }

        if (sourceObj.getClass().getAnnotation(MapManager.class).mappedWith() == null) {
            throw new IllegalArgumentException("The MapManager Annotation on the sourceObj " +
                    "must have mappedWith value not null");
        }

        Class<?> DTOClass = sourceObj.getClass().getAnnotation(MapManager.class).mappedWith();
        return map(sourceObj, DTOClass);
    }

    private static void mapFieldWithAnnotation(
            Field objField, Object sourceObj, Object dtoObj, Class<?> DTOclass)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        String ref = getRefOrThrowIfEmpty(objField);
        Field dtoField = getRefField(ref, DTOclass);
        setDTOField(objField, sourceObj, dtoField, dtoObj);
    }

    private static void mapFieldWithoutAnnotation(
            Field objField, Object sourceObj, Object dtoObj, Map<String, Field> nameToField)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        Field dtoField = nameToField.get(objField.getName());
        setDTOField(objField, sourceObj, dtoField, dtoObj);
    }

    private static void setDTOField(Field objField, Object sourceObj, Field dtoField, Object dtoObj)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        dtoField.setAccessible(true);
        objField.setAccessible(true);

        if (dtoField.getType().isArray()) {
            setDTOFieldWhenIsArray(objField, sourceObj, dtoField, dtoObj);
        } else {
            setDTOFieldWhenIsNotArray(objField, sourceObj, dtoField, dtoObj);
        }
    }

    private static void setDTOFieldWhenIsNotArray(
            Field objField, Object sourceObj, Field dtoField, Object dtoObj)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        if (dtoField.getType().isAnnotationPresent(Dto.class)) {
            dtoField.set(dtoObj, map(objField.get(sourceObj), dtoField.getType()));
        } else {
            if (!typesMatch(dtoField.getType(), objField.getType())) {
                throw new IllegalStateException(String.format("The types of the objField and the dtoField must be the same. " +
                                "But got %s for %s field (source) and %s for %s (dto).\n" +
                                "If names in the sourceObj and the dtoObj do not match, you must declare the mapping " +
                                "using the appropriate annotation.",
                        objField.getName(), objField.getType(), dtoField.getName(), dtoField.getType()));
            }
            dtoField.set(dtoObj, objField.get(sourceObj));
        }
    }

    private static void setDTOFieldWhenIsArray(
            Field objField, Object sourceObj, Field dtoField, Object dtoObj)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException, InstantiationException {
        if (dtoField.getType().getComponentType().isAnnotationPresent(Dto.class)) {
            setDTOFieldArrayWithAnnotation(objField, sourceObj, dtoField, dtoObj);
        } else {
            setDTOFieldArrayWithoutAnnotation(objField, sourceObj, dtoField, dtoObj);
        }
    }

    private static void setDTOFieldArrayWithAnnotation(
            Field objField, Object sourceObj, Field dtoField, Object dtoObj)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, InstantiationException {
        int size = Array.getLength(objField.get(sourceObj));
        if (size == 0) return;
        Object[] dtoArrayObj = (Object[]) Array.newInstance(dtoField.getType().getComponentType(), size);

        for (int i = 0; i < size; i++) {
            Object obj = Array.get(objField.get(sourceObj), i);
            Object dto = map(obj, dtoField.getType().getComponentType());
            Array.set(dtoArrayObj, i, dto);
        }
        dtoField.set(dtoObj, dtoArrayObj);
    }

    private static void setDTOFieldArrayWithoutAnnotation(
            Field objField, Object sourceObj, Field dtoField, Object dtoObj)
            throws IllegalAccessException {

        if (typesMatch(
                dtoField.getType().getComponentType(),
                objField.getType().getComponentType())
        ) {
            throw new IllegalStateException("The component type of the array in the objField " +
                    "and the component type of the array in the dtoField do not match. " +
                    "Map to the correct field on the DTO using the annotation " +
                    "or avoid naming fields with different types with the same name in the 2 class");
        }
        dtoField.set(dtoObj, objField.get(sourceObj));
    }

    private static Map<String, Field> createDTONameToField(Class<?> DTOClass) {
        Map<String, Field> map = new HashMap<>();
        for (Field field : DTOClass.getDeclaredFields()) {
            map.put(field.getName(), field);
        }
        return map;
    }

    private static String getRefOrThrowIfEmpty(Field field) {
        String ref = field.getAnnotation(Mapper.class).value();
        if (!ref.isEmpty()) return ref;
        throw new RuntimeException("The ref value in the Mapper annotation is empty. " +
                "If you use this annotation, you should specify a value for reference.");
    }

    private static Field getRefField(String ref, Class<?> DTOClass) {
        List<Field> fields = getMapReferenceFieldsByRef(ref, DTOClass);
        return getFieldOrThrow(fields);
    }

    private static List<Field> getMapReferenceFieldsByRef(String ref, Class<?> DTOClass) {
        List<Field> fields = new ArrayList<>();
        for (Field field : DTOClass.getDeclaredFields()) {
            if (field.getAnnotation(MapReference.class) != null &&
                    field.getAnnotation(MapReference.class).value().equals(ref)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static Field getFieldOrThrow(List<Field> fields) {
        if (fields.size() != 1) {
            throw new IllegalStateException(String.format("Expected just 1 reference for the source field, but got: %d ref on the DTOClass", fields.size()));
        }
        return fields.get(0);
    }

    private static boolean typesMatch(Type type1, Type type2) {
        return type1.equals(type2);
    }
}
