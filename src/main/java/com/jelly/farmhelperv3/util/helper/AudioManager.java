package com.jelly.farmhelperv3.util.helper;

import com.jelly.farmhelperv3.util.Tasks;
import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.util.LogUtils;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import com.jelly.farmhelperv3.event.Events.SubscribeEvent;
import com.jelly.farmhelperv3.event.Events.TickEvent;

import javax.sound.sampled.*;
import java.io.File;
import java.util.concurrent.TimeUnit;

public class AudioManager {
    private final Minecraft mc = Minecraft.getInstance();
    private static AudioManager instance;

    public static AudioManager getInstance() {
        if (instance == null) {
            instance = new AudioManager();
        }
        return instance;
    }

    @Getter
    @Setter
    private boolean minecraftSoundEnabled = false;

    private final Clock delayBetweenPings = new Clock();
    private int numSounds = 15;
    @Setter
    private float soundBeforeChange = 0;

    public void resetSound() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
            return;
        }
        minecraftSoundEnabled = false;
        if (FarmHelperConfig.maxOutMinecraftSounds) {
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) soundBeforeChange);
        }
    }

    private static Clip clip;

    public void playSound() {
        if (!FarmHelperConfig.failsafeSoundType) {
            if (minecraftSoundEnabled) return;
            numSounds = 15;
            minecraftSoundEnabled = true;
            if (FarmHelperConfig.maxOutMinecraftSounds) {
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) 1.0f);
            }
        } else {
            Tasks.background(() -> {
                try {
                    AudioInputStream inputStream = null;
                    switch (FarmHelperConfig.failsafeSoundSelected) {
                        case 0:
                            File audioFile = new File(mc.gameDirectory.getAbsolutePath() + "/farmhelper_sound.wav");
                            if (audioFile.exists() && audioFile.isFile())
                                inputStream = AudioSystem.getAudioInputStream(audioFile);
                            break;
                        case 1:
                            inputStream = AudioSystem.getAudioInputStream(getClass().getResource("/assets/farmhelperv3/sounds/staff_check_voice_notification.wav"));
                            break;
                        case 2:
                            inputStream = AudioSystem.getAudioInputStream(getClass().getResource("/assets/farmhelperv3/sounds/metal_pipe.wav"));
                            break;
                        case 3:
                            inputStream = AudioSystem.getAudioInputStream(getClass().getResource("/assets/farmhelperv3/sounds/aaaaaaaaaa.wav"));
                            break;
                        case 4:
                            inputStream = AudioSystem.getAudioInputStream(getClass().getResource("/assets/farmhelperv3/sounds/loud_buzz.wav"));
                            break;
                    }
                    if (inputStream == null) {
                        LogUtils.sendError("[Audio Manager] Failed to load sound file!");
                        return;
                    }
                    clip = AudioSystem.getClip();
                    clip.open(inputStream);
                    FloatControl volume = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                    float volumePercentage = FarmHelperConfig.failsafeSoundVolume / 100f;
                    float dB = (float) (Math.log(volumePercentage) / Math.log(10.0) * 20.0);
                    volume.setValue(dB);
                    clip.start();
                    clip.addLineListener(event -> {
                        if (event.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isSoundPlaying() {
        return (clip != null && clip.isRunning()) || minecraftSoundEnabled;
    }

    @SubscribeEvent
    public void click(TickEvent.ClientTickEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (FarmHelperConfig.failsafeSoundType) return;
        if (!minecraftSoundEnabled) return;
        if (delayBetweenPings.isScheduled() && !delayBetweenPings.passed()) return;
        if (numSounds <= 0) {
            minecraftSoundEnabled = false;
            if (FarmHelperConfig.maxOutMinecraftSounds) {
                mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) soundBeforeChange);
            }
            return;
        }

        switch (FarmHelperConfig.failsafeMcSoundSelected) {
            case 0: {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 10.0F, 1.0F);
                break;
            }
            case 1: {
                mc.player.playSound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, 10.0F, 1.0F);
                break;
            }
        }
        delayBetweenPings.schedule(100);
        numSounds--;
    }
}
