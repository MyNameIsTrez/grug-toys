package com.example.examplemod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class GameOfLifeBlockEntity extends GrugBlockEntity {
    public GameOfLifeBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.GAME_OF_LIFE_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();

        List<GrugEntity> grugEntities = Grug.grugEntitiesMap.get("foo:game_of_life_block_entity");
        if (grugEntities == null) {
            grugEntities = new ArrayList<GrugEntity>();
            Grug.grugEntitiesMap.put("foo:game_of_life_block_entity", grugEntities);
        }
        grugEntity.entitiesIndex = grugEntities.size();
        grugEntities.add(grugEntity);

        grugEntity.id = Grug.addEntity(EntityType.BlockEntity, this);

        worldPositionId = Grug.addEntity(EntityType.BlockPos, worldPosition);

        GrugFile file = new GrugFile();
        ExampleMod.grug.getEntityFile("foo:game_of_life_block_entity", file);

        grugEntity.globals = new byte[file.globalsSize];

        Set<Long> oldGlobalEntities = Grug.globalEntities;
        Grug.globalEntities = grugEntity.childEntities;
        Set<Long> oldFnEntities = Grug.fnEntities;
        Grug.fnEntities = grugEntity.childEntities;
        Set<Long> oldFnIteratedIterables = Grug.fnIteratedIterables;
        Grug.fnIteratedIterables = new HashSet<>();

        Grug.gameFunctionErrorHappened = false;
        ExampleMod.grug.callInitGlobals(file.initGlobalsFn, grugEntity.globals, grugEntity.id);

        Grug.globalEntities = oldGlobalEntities;
        Grug.fnEntities = oldFnEntities;
        Grug.fnIteratedIterables = oldFnIteratedIterables;

        grugEntity.onFns = file.onFns;

        spawn();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();

        Grug.removeEntity(grugEntity.id);
        Grug.removeEntity(worldPositionId);
        Grug.removeEntities(grugEntity.childEntities);

        // Swap-remove itself from Grug.entities
        List<GrugEntity> grugEntities = Grug.grugEntitiesMap.get("foo:game_of_life_block_entity");

        assert grugEntities != null;

        GrugEntity lastEntity = grugEntities.removeLast();

        // This check prevents the .set() from throwing
        // when grugEntity.entitiesIndex == grugEntities.size()
        if (grugEntity.entitiesIndex < grugEntities.size()) {
            grugEntities.set(grugEntity.entitiesIndex, lastEntity);

            lastEntity.entitiesIndex = grugEntity.entitiesIndex;
        }
    }

    public void spawn() {
        if (!ExampleMod.grug.block_entity_has_on_spawn(grugEntity.onFns)) {
            return;
        }

        Set<Long> oldGlobalEntities = Grug.globalEntities;
        Grug.globalEntities = grugEntity.childEntities;
        Set<Long> oldFnEntities = Grug.fnEntities;
        Grug.fnEntities = new HashSet<>();
        Set<Long> oldFnIteratedIterables = Grug.fnIteratedIterables;
        Grug.fnIteratedIterables = new HashSet<>();

        Grug.gameFunctionErrorHappened = false;
        ExampleMod.grug.block_entity_on_spawn(grugEntity.onFns, grugEntity.globals);

        Grug.globalEntities = oldGlobalEntities;
        Grug.removeEntities(Grug.fnEntities);
        Grug.fnEntities = oldFnEntities;
        Grug.fnIteratedIterables = oldFnIteratedIterables;
    }

    public void tick() {
        if (!ExampleMod.grug.block_entity_has_on_tick(grugEntity.onFns)) {
            return;
        }

        Set<Long> oldGlobalEntities = Grug.globalEntities;
        Grug.globalEntities = grugEntity.childEntities;
        Set<Long> oldFnEntities = Grug.fnEntities;
        Grug.fnEntities = new HashSet<>();
        Set<Long> oldFnIteratedIterables = Grug.fnIteratedIterables;
        Grug.fnIteratedIterables = new HashSet<>();

        Grug.gameFunctionErrorHappened = false;
        ExampleMod.grug.block_entity_on_tick(grugEntity.onFns, grugEntity.globals);

        Grug.globalEntities = oldGlobalEntities;
        Grug.removeEntities(Grug.fnEntities);
        Grug.fnEntities = oldFnEntities;
        Grug.fnIteratedIterables = oldFnIteratedIterables;
    }

    protected void neighborChanged(BlockState blockState, Level level, BlockPos blockPos, Block blockIn, BlockPos fromBlockPos, boolean isMoving) {
        if (!ExampleMod.grug.block_entity_has_on_neighbor_changed(grugEntity.onFns)) {
            return;
        }

        Set<Long> oldGlobalEntities = Grug.globalEntities;
        Grug.globalEntities = grugEntity.childEntities;
        Set<Long> oldFnEntities = Grug.fnEntities;
        Grug.fnEntities = new HashSet<>();
        Set<Long> oldFnIteratedIterables = Grug.fnIteratedIterables;
        Grug.fnIteratedIterables = new HashSet<>();

        long blockStateId = Grug.addEntity(EntityType.BlockState, blockState);
        Grug.fnEntities.add(blockStateId);

        long levelId = Grug.addEntity(EntityType.Level, level);
        Grug.fnEntities.add(levelId);

        long blockPosId = Grug.addEntity(EntityType.BlockPos, blockPos);
        Grug.fnEntities.add(blockPosId);

        long blockInId = Grug.addEntity(EntityType.Block, blockIn);
        Grug.fnEntities.add(blockInId);

        long fromBlockPosId = Grug.addEntity(EntityType.BlockPos, fromBlockPos);
        Grug.fnEntities.add(fromBlockPosId);

        Grug.gameFunctionErrorHappened = false;
        ExampleMod.grug.block_entity_on_neighbor_changed(grugEntity.onFns, grugEntity.globals, blockStateId, levelId, blockPosId, blockInId, fromBlockPosId, isMoving);

        Grug.globalEntities = oldGlobalEntities;
        Grug.removeEntities(Grug.fnEntities);
        Grug.fnEntities = oldFnEntities;
        Grug.fnIteratedIterables = oldFnIteratedIterables;
    }
}
