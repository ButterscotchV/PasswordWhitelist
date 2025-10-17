package io.github.gronnmann.passwordwhitelist

import net.md_5.bungee.api.ChatColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.metadata.FixedMetadataValue
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException
import java.time.Instant

class PasswordWhitelist : JavaPlugin(), Listener {
    private lateinit var approvedFile: File
    private lateinit var approvedConfig: FileConfiguration

    private lateinit var approvedList: MutableList<String>

    private var password: String? = null
    private var whitelistOnSuccess: Boolean = false
    private var banOnFail: Boolean = false
    private var maxFails: Int = 3

    private val ATTEMPT_KEY = "pw_attempts"

    override fun onEnable() {
        if (!dataFolder.exists()) dataFolder.mkdirs()

        // Approved players config
        approvedFile = File(dataFolder, "approved.yml")
        try {
            approvedFile.createNewFile()
        } catch (e: IOException) {
            logger.severe(e.toString())
        }
        approvedConfig = YamlConfiguration.loadConfiguration(approvedFile)
        approvedList = approvedConfig.getStringList("approved")

        // Password config
        saveDefaultConfig()
        password = config.getString("password")?.trim()
        if (password == null) {
            logger.warning("Password was not configured, proceeding without a password.")
        } else if (password == "CHANGE_ME") {
            logger.severe("Password is left as default! Please configure your server password.")
        }
        whitelistOnSuccess = config.getBoolean("whitelistOnSuccess", false)
        banOnFail = config.getBoolean("banOnFail", false)
        maxFails = config.getInt("maxFails", 3)

        Bukkit.getPluginManager().registerEvents(this, this)
    }

    override fun onDisable() {
        saveApproved()
    }

    private fun saveApproved() {
        approvedConfig.set("approved", approvedList)
        try {
            approvedConfig.save(approvedFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun approved(p: Player, sendMsg: Boolean = true): Boolean {
        // No password, consider player approved
        if (password.isNullOrEmpty()) return true

        if (approvedList.contains(p.uniqueId.toString()) || p.isWhitelisted) {
            return true
        } else {
            if (sendMsg) p.sendMessage(ChatColor.RED.toString() + "Enter the password to verify your account.")
            return false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun joinManager(e: PlayerJoinEvent) {
        if (!approved(e.player)) {
            e.player.gameMode = GameMode.ADVENTURE
            e.player.teleport(e.player.world.spawnLocation)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun checkForPassword(e: AsyncPlayerChatEvent) {
        if (approved(e.player, false)) return

        // Cancel all chat for unapproved players
        e.isCancelled = true

        // If the password is correct
        if (e.message.trim() == password) {
            // Reset the number of attempts
            e.player.removeMetadata(ATTEMPT_KEY, this)

            approvedList.add(e.player.uniqueId.toString())
            saveApproved()

            if (whitelistOnSuccess) {
                e.player.isWhitelisted = true
            }

            e.player.sendMessage(ChatColor.GREEN.toString() + "Verified successfully.")
            Bukkit.getScheduler().runTask(this, Runnable {
                e.player.gameMode = Bukkit.getServer().defaultGameMode
            })
        } else {
            // Last fail count plus one for this failure
            val curFails = (e.player.getMetadata(ATTEMPT_KEY).firstOrNull {
                it.owningPlugin?.name == name
            }?.asInt() ?: 0) + 1

            // If max failures is exceeded, remove the player
            if (maxFails > 0 && curFails >= maxFails) {
                // Reset the number of attempts (so if they're unbanned, they may try again)
                e.player.removeMetadata(ATTEMPT_KEY, this)

                if (banOnFail) {
                    e.player.ban(
                        ChatColor.RED.toString() + "Too many incorrect password attempts.", Instant.MAX, name, true
                    )
                } else {
                    e.player.kickPlayer(ChatColor.RED.toString() + "Too many incorrect password attempts.")
                }
            } else {
                // Save the new failure count
                val newMeta = FixedMetadataValue(this, curFails)
                e.player.setMetadata(ATTEMPT_KEY, newMeta)

                if (maxFails > 0) {
                    e.player.sendMessage(ChatColor.RED.toString() + "Incorrect password, try again ($curFails/$maxFails).")
                } else {
                    e.player.sendMessage(ChatColor.RED.toString() + "Incorrect password, try again.")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopInteract(e: PlayerInteractEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopPlace(e: BlockPlaceEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopBreak(e: BlockBreakEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopDrop(e: PlayerDropItemEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopPickup(e: EntityPickupItemEvent) {
        if (e.entity !is Player) return
        if (!approved(e.entity as Player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopEntityTarget(e: EntityTargetEvent) {
        if (e.target !is Player) return
        if (!approved(e.target as Player, false)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopAttack(e: EntityDamageByEntityEvent) {
        if (e.damager is Player && !approved(e.damager as Player)) e.isCancelled = true
        else if (e.entity is Player && !approved(e.entity as Player, false)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun disallowCommands(e: PlayerCommandPreprocessEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun stopMovement(e: PlayerMoveEvent) {
        if (!approved(e.player, false)) e.isCancelled = true
    }
}
