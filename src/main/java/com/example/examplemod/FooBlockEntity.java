package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class FooBlockEntity extends BlockEntity {
    public FooBlockEntity(BlockPos pos, BlockState state) {
        super(ExampleMod.FOO_BLOCK_ENTITY.get(), pos, state);

        // TODO: Update the id stored in the globals ptr to the address of `this`,
        // TODO: so that Grug.gameFn_getWorldPosition() can use that
        // TODO: to access this.worldPosition
    }

    public void tick() {
        if (!ExampleMod.grug.blockEntity_has_onTick(Grug.tempFooBlockEntity.onFns)) {
            return;
        }

        ExampleMod.grug.blockEntity_onTick(Grug.tempFooBlockEntity.onFns, Grug.tempFooBlockEntityGlobals);
    }
}
