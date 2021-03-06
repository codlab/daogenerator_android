package com.mobilebuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;

import de.greenrobot.daogenerator.DaoGenerator;
import de.greenrobot.daogenerator.Entity;
import de.greenrobot.daogenerator.Property;
import de.greenrobot.daogenerator.Schema;

public class InternalDaoGenerator {

    private static enum PropertyType {
        DATE,
        DOUBLE,
        FLOAT,
        STRING,
        INTEGER,
        BOOLEAN,
        LONG,
        BLOB
    }

    private static enum RelationType {
        TO_MANY,
        HAS_ONE,
        HAS_MANY
    }

    //SYSTEM PROPERTIES
    private static final String DAOGENERATOR_SCHEMA_PATH = "DAOGENERATOR_SCHEMA_PATH";

    //IO
    private static final String SCHEMA = "schema.json";
    private static final String OUTPUT_SCHEMA_INEXISTANT = "No schema.json in %s";
    private static final String OUTPUT_MISSING_OUTPUT_DIR = "Invalid output_dir in %s's schema.json";

    //GEN
    private static final String SCHEMA_OUTPUT_DIR = "output_dir";
    private static final String SCHEMA_PATH = System.getProperty(DAOGENERATOR_SCHEMA_PATH, "/../app/src/main/assets/schema.json");
    private static final String PACKAGE_NAME_KEY = "packageName";
    private static final String DATABASE_VERSION_KEY = "databaseVersion";
    private static final String TABLES_KEY = "tables";

    //TABLE
    private static final String TABLE_NAME_KEY = "name";
    private static final String TABLE_PROPERTIES_KEY = "properties";

    //PROPERTY
    private static final String TABLE_PROPERTY_NAME_KEY = "name";
    private static final String TABLE_PROPERTY_TYPE_KEY = "type";
    private static final String TABLE_PROPERTY_MANDATORY_KEY = "mandatory";
    private static final String TABLE_PROPERTY_INDEXED_KEY = "indexed";

    //RELATIONS
    private static final String RELATION_KEY = "relationships";
    private static final String RELATION_NAME_KEY = "name";
    private static final String RELATION_LEFT_TABLE_KEY = "left_table";
    private static final String RELATION_RIGHT_TABLE_KEY = "right_table";
    private static final String RELATION_TYPE_KEY = "type";


    public static void main(String[] args) throws Exception {
        for (String path : args) {
            System.out.println("inspecting " + path);
            inspectPath(path);
            System.out.println("\n");
        }
    }

    private static void inspectPath(String path) {
        String filePath = new File(path, SCHEMA).getAbsolutePath();

        File file = new File(filePath);
        FileInputStream fis = null;
        StringBuilder data;

        if (!file.exists()) {
            System.out.println(String.format(OUTPUT_SCHEMA_INEXISTANT, file.getAbsolutePath()));
            return;
        }

        try {

            fis = new FileInputStream(file);
            data = new StringBuilder(fis.available());

            int content;
            while ((content = fis.read()) != -1) {
                data.append((char) content);
            }

            JSONObject j = new JSONObject(data.toString());

            String output_dir = j.optString(SCHEMA_OUTPUT_DIR, null);
            if (output_dir == null) {
                System.out.println(String.format(OUTPUT_MISSING_OUTPUT_DIR, path));
                return;
            }

            Schema schema = new Schema(j.optInt(DATABASE_VERSION_KEY, 1), j.optString(PACKAGE_NAME_KEY, "db"));
            JSONArray tablesArray = j.optJSONArray(TABLES_KEY);
            addTables(schema, tablesArray);

            JSONArray relationsArray = j.optJSONArray(RELATION_KEY);
            setupRelations(schema, relationsArray);

            new DaoGenerator().generateAll(schema, new File(path, output_dir).getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fis != null)
                    fis.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void addTables(final Schema schema, final JSONArray tablesArray) {
        if (tablesArray != null && tablesArray.length() > 0) {
            for (int idx = 0, size = tablesArray.length(); idx < size; idx++) {
                JSONObject table = tablesArray.optJSONObject(idx);
                if (table != null) {
                    Entity entity = schema.addEntity(table.getString(TABLE_NAME_KEY));
                    entity.setHasKeepSections(true);
                    entity.addIdProperty();
                    addTableProperties(schema, entity, table.optJSONArray(TABLE_PROPERTIES_KEY));
                }
            }
        }
    }

    private static void addTableProperties(final Schema schema, final Entity entity, final JSONArray properties) {
        if (properties != null && properties.length() > 0) {
            for (int idx = 0, size = properties.length(); idx < size; idx++) {
                JSONObject property = properties.optJSONObject(idx);
                if (property != null) {
                    addTableProperty(schema, entity, property);
                }
            }
        }
    }

    private static void addTableProperty(final Schema schema, final Entity entity, final JSONObject property) {
        PropertyType type = PropertyType.valueOf(property.optString(TABLE_PROPERTY_TYPE_KEY).toUpperCase());
        Property.PropertyBuilder col;
        switch (type) {
            case INTEGER:
                col = entity.addIntProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case BOOLEAN:
                col = entity.addBooleanProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case LONG:
                col = entity.addLongProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case DATE:
                col = entity.addDateProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case DOUBLE:
                col = entity.addDoubleProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case BLOB:
                col = entity.addByteArrayProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case FLOAT:
                col = entity.addFloatProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
            case STRING:
            default:
                col = entity.addStringProperty(property.getString(TABLE_PROPERTY_NAME_KEY));
                break;
        }

        boolean mandatory = property.optBoolean(TABLE_PROPERTY_MANDATORY_KEY, false);
        if (mandatory) col.notNull();

        boolean indexed = property.optBoolean(TABLE_PROPERTY_INDEXED_KEY, false);
        if (indexed) col.index();
    }

    private static void setupRelations(final Schema schema, final JSONArray relationsArray) {
        if (relationsArray != null && relationsArray.length() > 0) {
            List<Entity> entityList = schema.getEntities();

            for (int idx = 0, size = relationsArray.length(); idx < size; idx++) {
                JSONObject relation = relationsArray.optJSONObject(idx);
                if (relation != null) {
                    RelationType type = RelationType.valueOf(relation.optString(RELATION_TYPE_KEY).toUpperCase());
                    String relationName = relation.getString(RELATION_NAME_KEY);
                    boolean mandatory = relation.optBoolean(TABLE_PROPERTY_MANDATORY_KEY, false);
                    final Entity leftEntity = getEntityWithName(entityList, relation.getString(RELATION_LEFT_TABLE_KEY));
                    final Entity rightEntity = getEntityWithName(entityList, relation.getString(RELATION_RIGHT_TABLE_KEY));
                    if (leftEntity != null && rightEntity != null) {
                        switch (type) {
                            case TO_MANY: {
                                Property baseId = mandatory ? rightEntity.addLongProperty("parentId").notNull().getProperty() : rightEntity.addLongProperty("parentId").getProperty();
                                rightEntity.addToOne(leftEntity, baseId);
                                leftEntity.addToMany(rightEntity, baseId, "childs" + rightEntity.getClassName());
                            }
                            break;
                            case HAS_ONE: {
                                String relation_name = relationName + "Id";
                                Property baseId = mandatory ? leftEntity.addLongProperty(relation_name).notNull().getProperty() : leftEntity.addLongProperty(relation_name).getProperty();
                                leftEntity.addToOne(rightEntity, baseId, relationName);

                            }
                            break;
                            case HAS_MANY: {
                                leftEntity.addToOneWithoutProperty(relationName, rightEntity, relationName);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    private static Entity getEntityWithName(final List<Entity> entityList, final String name) {
        if (entityList != null && entityList.size() > 0) {
            for (Entity entity : entityList) {
                if (entity.getClassName().equals(name))
                    return entity;
            }
        }

        return null;
    }
}
