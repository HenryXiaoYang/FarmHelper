package com.jelly.farmhelperv3.config.page;

import com.jelly.farmhelperv3.config.Setting;


public class FailsafeNotificationsPage {
    @Setting(kind = Setting.Kind.SWITCH,
            name = "Rotation Check Notifications",
            description = "Whether or not to send a notification when the rotation check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnRotationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Teleportation Check Notifications",
            description = "Whether or not to send a notification when the teleportation check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnTeleportationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lag Back Notifications",
            description = "Whether or not to send a notification when the lag back failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnLagBackFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Knockback Check Notifications",
            description = "Whether or not to send a notification when the knockback check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnKnockbackFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Dirt Check Notifications",
            description = "Whether or not to send a notification when the dirt check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnDirtFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Cobweb Check Notifications",
            description = "Whether or not to send a notification when the cobweb check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnCobwebFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Item Change Check Notifications",
            description = "Whether or not to send a notification when the item change check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnItemChangeFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Level Change Check Notifications",
            description = "Whether or not to send a notification when the world change check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnWorldChangeFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bedrock Cage Check Notifications",
            description = "Whether or not to send a notification when the bedrock cage check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnBedrockCageFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bad Effects Check Notifications",
            description = "Whether or not to send a notification when the bad effects check failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnBadEffectsFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Evacuate Notifications",
            description = "Whether or not to send a notification when the evacuate failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnEvacuateFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Banwave Notifications",
            description = "Whether or not to send a notification when the banwave failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnBanwaveFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Disconnect Notifications",
            description = "Whether or not to send a notification when the disconnect failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnDisconnectFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Jacob Notifications",
            description = "Whether or not to send a notification when the Jacob failsafe is triggered.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnJacobFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lower Average BPS Notifications",
            description = "Whether or not to send a notification when the average BPS is lower than the specified value.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnLowerAverageBPS = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Guest Visit Notifications",
            description = "Whether or not to send a notification when a guest visits your island.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnGuestVisit = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Full Inventory Notifications",
            description = "Whether or not to send a notification when your inventory is full.",
            category = "Failsafe Notifications"
    )
    public static boolean notifyOnInventoryFull = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Rotation Check Sound Alert",
            description = "Whether or not to play a sound when the rotation check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnRotationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Teleportation Check Sound Alert",
            description = "Whether or not to play a sound when the teleportation check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnTeleportationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Knockback Check Sound Alert",
            description = "Whether or not to play a sound when the knockback check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnKnockbackFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Dirt Check Sound Alert",
            description = "Whether or not to play a sound when the dirt check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnDirtFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Cobweb Check Sound Alert",
            description = "Whether or not to play a sound when the cobweb check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnCobwebFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Item Change Check Sound Alert",
            description = "Whether or not to play a sound when the item change check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnItemChangeFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Level Change Check Sound Alert",
            description = "Whether or not to play a sound when the world change check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnWorldChangeFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bedrock Cage Check Sound Alert",
            description = "Whether or not to play a sound when the bedrock cage check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnBedrockCageFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bad Effects Check Sound Alert",
            description = "Whether or not to play a sound when the bad effects check failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnBadEffectsFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Evacuate Alert",
            description = "Whether or not to play a sound when the evacuate failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnEvacuateFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Banwave Alert",
            description = "Whether or not to play a sound when the banwave failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnBanwaveFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Disconnect Alert",
            description = "Whether or not to play a sound when the disconnect failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnDisconnectFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Jacob Alert",
            description = "Whether or not to play a sound when the Jacob failsafe is triggered.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnJacobFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Full Inventory Alert",
            description = "Whether or not to play a sound when your inventory is full.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnFullInventory = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lower Average BPS Alert",
            description = "Whether or not to play a sound when the average BPS is lower than the specified value.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnLowerAverageBPS = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Guest Visit Alert",
            description = "Whether or not to play a sound when a guest visits your island.",
            category = "Failsafe Sound Alerts"
    )
    public static boolean alertOnGuestVisit = false;


    @Setting(kind = Setting.Kind.SWITCH,
            name = "Rotation Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the rotation check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnRotationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Teleportation Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the teleportation check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnTeleportationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lag Back Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the lag back failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnLagBackFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Knockback Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the knockback check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnKnockbackFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Dirt Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the dirt check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnDirtFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Cobweb Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the cobweb check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnCobwebFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Item Change Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the item change check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnItemChangeFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Level Change Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the world change check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnWorldChangeFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bedrock Cage Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the bedrock cage check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnBedrockCageFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bad Effects Check Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the bad effects check failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnBadEffectsFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Evacuate Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the evacuate failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnEvacuateFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Banwave Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the banwave failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnBanwaveFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Disconnect Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the disconnect failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnDisconnectFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Jacob Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the Jacob failsafe is triggered.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnJacobFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lower Average BPS Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when the average BPS is lower than the specified value.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnLowerAverageBPS = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Guest Visit Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when a guest visits your island.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnGuestVisit = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Full Inventory Tag Everyone",
            description = "Whether or not to tag everyone in the webhook message when your inventory is full.",
            category = "Failsafe Tag Everyone"
    )
    public static boolean tagEveryoneOnFullInventory = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Rotation Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the rotation check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnRotationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Teleportation Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the teleportation check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnTeleportationFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Knockback Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the knockback check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnKnockbackFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Dirt Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the dirt check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnDirtFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Cobweb Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the cobweb check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnCobwebFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Item Change Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the item change check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnItemChangeFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Level Change Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the world change check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnWorldChangeFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bedrock Cage Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the bedrock cage check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnBedrockCageFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Bad Effects Check Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the bad effects check failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnBadEffectsFailsafe = true;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Evacuate Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the evacuate failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnEvacuateFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Banwave Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the banwave failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnBanwaveFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Disconnect Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the disconnect failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnDisconnectFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Jacob Auto Alt-tab",
            description = "Whether or not to automatically alt-tab when the Jacob failsafe is triggered.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnJacobFailsafe = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Lower Average BPS Alt-tab",
            description = "Whether or not to automatically alt-tab when the average BPS is lower than the specified value.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnLowerAverageBPS = true;
    @Setting(kind = Setting.Kind.SWITCH,
            name = "Guest Visit Alt-tab",
            description = "Whether or not to automatically alt-tab when a guest visits your island.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnGuestVisit = false;

    @Setting(kind = Setting.Kind.SWITCH,
            name = "Full inventory Alt-tab",
            description = "Whether or not to automatically alt-tab when your inventory is full.",
            category = "Failsafe Auto Alt-tab"
    )
    public static boolean autoAltTabOnInventoryFull = false;
}
