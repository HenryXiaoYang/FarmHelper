package com.jelly.farmhelperv3.mixin.gui;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSignEditScreen.class)
public interface AccessorGuiEditSign {
    @Accessor("messages") String[] farmhelper$messages();
    @Accessor("line") void farmhelper$line(int line);
    @Invoker("setMessage") void farmhelper$setMessage(String text);
    @Invoker("onDone") void farmhelper$done();
}
