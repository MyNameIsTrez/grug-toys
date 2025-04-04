package com.example.examplemod;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.server.ServerLifecycleHooks;

public class Grug {
    private native void loadGlobalLibraries();

    private native void initGrugAdapter();

    private native boolean grugInit(String modApiJsonPath, String modsDirPath);

    private native void setOnFnsToFastMode();

    private native boolean grugRegenerateModifiedMods();

    private native boolean errorHasChanged();
    private native boolean loadingErrorInGrugFile();
    private native String errorMsg();
    private native String errorPath();
    private native int errorGrugCLineNumber();

    private native int getGrugReloadsSize();
    private native void fillReloadData(ReloadData reloadData, int i);

    private native void fillRootGrugDir(GrugDir root);
    private native void fillGrugDir(GrugDir dir, long parentDirAddress, int dirIndex);
    private native void fillGrugFile(GrugFile file, long parentDirAddress, int fileIndex);

    public native void callInitGlobals(long initGlobalsFn, byte[] globals, long id);

    public native void getEntityFile(String entity, GrugFile file);

    // TODO: This does not recycle indices of despawned entities,
    // TODO: which means this will eventually wrap around back to 0
    private static Map<EntityType, Integer> nextEntityIndices = new HashMap<>();
    private static Map<Long, Object> entityData = new HashMap<>();

    public native boolean block_entity_has_on_spawn(long onFns);
    public native void block_entity_on_spawn(long onFns, byte[] globals);

    public native boolean block_entity_has_on_tick(long onFns);
    public native void block_entity_on_tick(long onFns, byte[] globals);

    private ReloadData reloadData = new ReloadData();

    public static Map<String, List<GrugEntity>> grugEntitiesMap = new HashMap<String, List<GrugEntity>>();

    // This is deliberately not assigned a new List.
    // This variable gets assigned an entity's list of child IDs before init_globals() is called,
    // and it gets assigned the below onFnEntities before an on_ function is called.
    public static List<Long> fnEntities;

    // This is deliberately not assigned a new List.
    // This variable gets assigned an entity's list of child IDs before on_ functions are called.
    // This allows on_ functions to add copies of entities to global data structures, like HashSets.
    public static List<Long> globalEntities;

    // Cleared at the end of every on_ fn call.
    public static List<Long> onFnEntities = new ArrayList<>();

    public static boolean gameFunctionErrorHappened = false;

    private static HashMap<Long, HashSet<Object>> allHashSetObjects = new HashMap<>();

    public Grug() {
        try {
            extractAndLoadNativeLibrary("libglobal_library_loader.so");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        loadGlobalLibraries();

        try {
            extractAndLoadNativeLibrary("libadapter.so");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        initGrugAdapter();

        if (grugInit("../mod_api.json", "../mods")) {
            throw new RuntimeException("grugInit() error: " + errorMsg() + " (detected by grug.c:" + errorGrugCLineNumber() + ")");
        }

        // We need to regenerate the mods before the first modded entities are instantiated,
        // since they call getEntityFile() in their constructors
        if (grugRegenerateModifiedMods()) {
            if (loadingErrorInGrugFile()) {
                throw new RuntimeException("grug loading error: " + errorMsg() + "\nDetected in " + errorPath() + " by grug.c:" + errorGrugCLineNumber());
            } else {
                throw new RuntimeException("grug loading error: " + errorMsg() + "\nDetected by grug.c:" + errorGrugCLineNumber());
            }
        }

        for (EntityType entityType : EntityType.values()) {
            nextEntityIndices.put(entityType, 0);
        }
    }

    private void extractAndLoadNativeLibrary(String libraryName) throws IOException {
        // Get the native library file from the JAR's resources
        String libraryPathInJar = "/natives/" + libraryName;
        InputStream libraryInputStream = getClass().getResourceAsStream(libraryPathInJar);

        if (libraryInputStream == null) {
            throw new IOException("Native library not found in the JAR: " + libraryName);
        }

        // Create a temporary file
        String baseName = libraryName.substring(0, libraryName.lastIndexOf('.'));
        String extension = libraryName.substring(libraryName.lastIndexOf('.'));
        Path tempLibraryPath = Files.createTempFile(baseName, extension);

        // Extract the library to the temporary file
        try (OutputStream out = new FileOutputStream(tempLibraryPath.toFile())) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = libraryInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        // Make sure the extracted file is executable (for Linux/Mac)
        tempLibraryPath.toFile().setExecutable(true);

        // Load the library from the temporary file
        System.load(tempLibraryPath.toAbsolutePath().toString());
    }

    public void runtimeErrorHandler(String reason, int type, String onFnName, String onFnPath) {
        sendMessageToEveryone(
            Component.literal("grug runtime error in ").withColor(ChatFormatting.RED.getColor())
            .append(Component.literal(onFnName + "()").withColor(0xc3e88d))
            .append(Component.literal(": ").withColor(ChatFormatting.RED.getColor()))
            .append(Component.literal(reason).withColor(ChatFormatting.WHITE.getColor()))
            .append("\nDetected in ")
            .append(Component.literal(onFnPath).withColor(ChatFormatting.DARK_AQUA.getColor()))
        );
    }

    public void onTick() {
        if (grugRegenerateModifiedMods()) {
            if (loadingErrorInGrugFile()) {
                sendMessageToEveryone(
                    Component.literal("grug loading error: ").withColor(ChatFormatting.RED.getColor())
                    .append(Component.literal(errorMsg()).withColor(ChatFormatting.WHITE.getColor()))
                    .append(Component.literal("\nDetected in ").withColor(ChatFormatting.RED.getColor()))
                    .append(Component.literal(errorPath()).withColor(ChatFormatting.DARK_AQUA.getColor()))
                    .append(Component.literal(" by "))
                    .append(Component.literal("grug.c:" + errorGrugCLineNumber()).withColor(ChatFormatting.DARK_AQUA.getColor()))
                );
            } else {
                sendMessageToEveryone(
                    Component.literal("grug loading error: ").withColor(ChatFormatting.RED.getColor())
                    .append(Component.literal(errorMsg()).withColor(ChatFormatting.WHITE.getColor()))
                    .append(Component.literal("\nDetected by "))
                    .append(Component.literal("grug.c:" + errorGrugCLineNumber()).withColor(ChatFormatting.DARK_AQUA.getColor()))
                );
            }

            return;
        }

        reloadModifiedEntities();

        // reloadModifiedResources();
    }

    public void reloadModifiedEntities() {
        int reloadsSize = getGrugReloadsSize();

        for (int reloadIndex = 0; reloadIndex < reloadsSize; reloadIndex++) {
            fillReloadData(reloadData, reloadIndex);

            GrugFile file = reloadData.file;

            List<GrugEntity> grugEntities = grugEntitiesMap.get(file.entity);
            if (grugEntities == null) {
                continue;
            }

            for (GrugEntity grugEntity : grugEntities) {
                grugEntity.globals = new byte[file.globalsSize];

                grugEntity.childEntities.clear();
                Grug.fnEntities = grugEntity.childEntities;
                Grug.gameFunctionErrorHappened = false;
                callInitGlobals(file.initGlobalsFn, grugEntity.globals, grugEntity.id);
                Grug.fnEntities = Grug.onFnEntities;

                grugEntity.onFns = file.onFns;
            }
        }
    }

    public static void sendMessageToEveryone(Component message) {
        // System.err.println(message);

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer player : level.players()) {
                player.sendSystemMessage(message);
            }
        }
    }

    public static void sendGameFunctionErrorToEveryone(String gameFunctionName, String message) {
        sendMessageToEveryone(
            Component.literal("grug game function error in ").withColor(ChatFormatting.RED.getColor())
            .append(Component.literal(gameFunctionName + "()").withColor(0xc792ea))
            .append(Component.literal(": ").withColor(ChatFormatting.RED.getColor()))
            .append(Component.literal(message).withColor(ChatFormatting.WHITE.getColor()))
        );
    }

    public static long addEntity(EntityType entityType, Object entityInstance) {
        // System.out.println("nextEntityIndices: " + nextEntityIndices);
        // System.out.println("entityData: " + entityData);
        // System.out.println("entityInstance: " + entityInstance);

        int entityIndex = nextEntityIndices.get(entityType);

        nextEntityIndices.put(entityType, entityIndex + 1);

        long id = getEntityID(entityType, entityIndex);

        entityData.put(id, entityInstance);

        // System.out.println("entityType: " + entityType);
        // System.out.println("entityIndex: " + entityIndex);
        // System.out.println("returned id: " + id);

        return id;
    }

    public static void removeEntity(long id) {
        // System.out.println("id: " + id);
        // System.out.println("getEntityType(id): " + getEntityType(id));
        // System.out.println("getEntityIndex(id): " + getEntityIndex(id));

        entityData.remove(id);
    }

    public static void removeEntities(Iterable<Long> entities) {
        for (long entity : entities) {
            removeEntity(entity);
        }
    }

    private static long getEntityID(EntityType entityType, int entityIndex) {
        return (long)entityType.ordinal() << 32 | entityIndex;
    }

    public static EntityType getEntityType(long id) {
        return EntityType.get((int)(id >> 32));
    }

    // private int getEntityIndex(long id) {
    //     return (int)(id & 0xffffffff);
    // }

    // TODO: I'm sure this can be done cleaner
    public static boolean isEntityTypeInstanceOf(EntityType derived, EntityType base) {
        if (derived == base) {
            return true;
        }
        if (derived == EntityType.ItemEntity && base == EntityType.Entity) {
            return true;
        }
        return false;
    }

    private static void assertEntityType(long id, EntityType expectedEntityType) {
        EntityType entityType = getEntityType(id);
        if (!isEntityTypeInstanceOf(entityType, expectedEntityType)) {
            throw new AssertEntityTypeException(entityType, expectedEntityType);
        }
    }

    public static long entityCopyForDataStructure(long id, Object object, long dataStructureToId) {
        // We even add a new entity to local hash sets,
        // as it would be annoying if hash_set_clear() and hash_set_copy()
        // would need to check for every entity whether it is in Grug.globalEntities
        long newId = Grug.addEntity(Grug.getEntityType(id), object);

        if (Grug.globalEntities.contains(dataStructureToId)) {
            Grug.globalEntities.add(newId);
        } else {
            Grug.fnEntities.add(newId);
        }

        return newId;
    }

    public static void newHashSetObjects(long hashSetId) {
        allHashSetObjects.put(hashSetId, new HashSet<>());
    }

    public static HashSet<Object> getHashSetObjects(long hashSetId) {
        return allHashSetObjects.get(hashSetId);
    }

    public Block getBlock(long id) {
        assertEntityType(id, EntityType.Block);
        return (Block)entityData.get(id);
    }

    public GrugBlockEntity getBlockEntity(long id) {
        assertEntityType(id, EntityType.BlockEntity);
        return (GrugBlockEntity)entityData.get(id);
    }

    public BlockPos getBlockPos(long id) {
        assertEntityType(id, EntityType.BlockPos);
        return (BlockPos)entityData.get(id);
    }

    public BlockState getBlockState(long id) {
        assertEntityType(id, EntityType.BlockState);
        return (BlockState)entityData.get(id);
    }

    public Entity getEntity(long id) {
        assertEntityType(id, EntityType.Entity);
        return (Entity)entityData.get(id);
    }

    @SuppressWarnings("unchecked")
    public HashSet<Long> getHashSet(long id) {
        assertEntityType(id, EntityType.HashSet);
        return (HashSet<Long>)entityData.get(id);
    }

    public Item getItem(long id) {
        assertEntityType(id, EntityType.Item);
        return (Item)entityData.get(id);
    }

    public ItemEntity getItemEntity(long id) {
        assertEntityType(id, EntityType.ItemEntity);
        return (ItemEntity)entityData.get(id);
    }

    public ItemStack getItemStack(long id) {
        assertEntityType(id, EntityType.ItemStack);
        return (ItemStack)entityData.get(id);
    }

    @SuppressWarnings("unchecked")
    public Iterator<Long> getIterator(long id) {
        assertEntityType(id, EntityType.Iterator);
        return (Iterator<Long>)entityData.get(id);
    }

    public Level getLevel(long id) {
        assertEntityType(id, EntityType.Level);
        return (Level)entityData.get(id);
    }

    public Object getObject(long id) {
        return entityData.get(id);
    }

    public ResourceLocation getResourceLocation(long id) {
        assertEntityType(id, EntityType.ResourceLocation);
        return (ResourceLocation)entityData.get(id);
    }

    public Vec3 getVec3(long id) {
        assertEntityType(id, EntityType.Vec3);
        return (Vec3)entityData.get(id);
    }
}
