package com.jelly.farmhelperv3.config.page;

import com.jelly.farmhelperv3.config.Setting;


/*
    Credits to Yuro for this superb class
*/
public class CustomFailsafeMessagesPage {
    @Setting(kind = Setting.Kind.TEXT,
            name = "Custom messages sent during Jacob's Contest",
            description = "The messages to send to the chat when the failsafe has been triggered and you are during Jacob's Contest (use '|' to split the messages)",
            placeholder = "Leave empty to disable",
            multiline = true
    )
    public static String customJacobMessages = "";
    @Setting(kind = Setting.Kind.SLIDER,
            name = "Custom Jacob's Contest message chance",
            description = "The chance that the custom Jacob's Contest message will be sent to the chat",
            min = 0,
            max = 100
    )
    public static int customJacobChance = 50;

    @Setting(kind = Setting.Kind.TEXT,
            name = "Custom continue messages",
            description = "The messages to send to the chat when the failsafe has been triggered and you want to ask if you can continue (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customContinueMessages = "";
    @Setting(kind = Setting.Kind.TEXT,
            name = "Rotation failsafe messages",
            description = "The messages to send to the chat when the rotation failsafe has been triggered (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customRotationMessages = "";
    @Setting(kind = Setting.Kind.TEXT,
            name = "Teleportation failsafe messages",
            description = "The messages to send to the chat when the teleportation failsafe has been triggered (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customTeleportationMessages = "";
    @Setting(kind = Setting.Kind.TEXT,
            name = "Knockback failsafe messages",
            description = "The messages to send to the chat when the knockback failsafe has been triggered (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customKnockbackMessages = "";

    @Setting(kind = Setting.Kind.TEXT,
            name = "Bedrock failsafe messages",
            description = "The messages to send to the chat when the bedrock failsafe has been triggered (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customBedrockMessages = "";

    @Setting(kind = Setting.Kind.TEXT,
            name = "Dirt failsafe messages",
            description = "The messages to send to the chat when the dirt failsafe has been triggered (use '|' to split the messages)",
            placeholder = "Leave empty to use a random message",
            multiline = true
    )
    public static String customDirtMessages = "";
}
