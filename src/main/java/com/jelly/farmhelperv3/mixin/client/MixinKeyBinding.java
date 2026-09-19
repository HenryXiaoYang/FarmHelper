package com.jelly.farmhelperv3.mixin.client;

import com.jelly.farmhelperv3.handler.MacroHandler;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Arrays;

@Mixin(KeyMapping.class)
public abstract class MixinKeyBinding {
    @Shadow private int clickCount;
    @Shadow private boolean isDown;
    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true)
    private void farmhelper$lockSlots(CallbackInfoReturnable<Boolean> cir) {
        if (!com.jelly.farmhelperv3.FarmHelperClient.ready) return;
        if (!MacroHandler.getInstance().isMacroToggled() || MacroHandler.getInstance().isCurrentMacroPaused()) return;
        var options = Minecraft.getInstance().options;
        if ((Object)this == options.keyDrop || Arrays.stream(options.keyHotbarSlots).anyMatch(key -> key == (Object)this)) {
            clickCount = 0; isDown = false; cir.setReturnValue(false);
        }
    }
}
