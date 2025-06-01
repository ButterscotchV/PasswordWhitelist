package io.github.gronnmann.passwordwhitelist

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityTargetEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.IOException

class PasswordWhitelist : JavaPlugin(), Listener {
    private lateinit var approvedFile: File
    private lateinit var approvedConfig: FileConfiguration

    private var approvedList: MutableList<String> = mutableListOf()

    private var password: String? = null

    override fun onEnable() {
        if (!dataFolder.exists()) dataFolder.mkdirs()

        approvedFile = File(dataFolder, "approved.yml")
        try {
            approvedFile.createNewFile()
        } catch (e: IOException) {
            logger.severe(e.toString())
        }
        approvedConfig = YamlConfiguration.loadConfiguration(approvedFile)
        approvedList = approvedConfig.getStringList("approved")

        saveDefaultConfig()

        password = config.getString("password")?.trim()
        if (password == null) {
            logger.warning("Password was not configured, proceeding without a password.")
        }

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
        val player = p.uniqueId.toString()

        if (approvedList.contains(player)) {
            return true
        } else {
            if (sendMsg) p.sendMessage(ChatColor.RED.char + "Enter password to interact with the world.")
            return false
        }
    }

    @EventHandler
    fun joinManager(e: PlayerJoinEvent) {
        if (!approved(e.player)) {
            e.player.gameMode = GameMode.ADVENTURE
            e.player.teleport(e.player.world.spawnLocation)
        }
    }

    @EventHandler
    fun checkForPassword(e: AsyncPlayerChatEvent) {
        if (!approved(e.player, false)) {
            // Cancel all chat for unapproved players
            e.isCancelled = true

            if (e.message.trim() == password) {
                approvedList.add(e.player.uniqueId.toString())
                saveApproved()

                e.player.sendMessage(ChatColor.GREEN.char + "Verified successfully.")
                Bukkit.getScheduler().runTask(this, Runnable {
                    e.player.gameMode = Bukkit.getServer().defaultGameMode
                })
            } else {
                e.player.sendMessage(ChatColor.RED.char + "Incorrect password, try again.")
            }
        }
    }

    @EventHandler
    fun stopInteract(e: PlayerInteractEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun stopDrop(e: PlayerDropItemEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }

    @EventHandler
    fun stopPickup(e: EntityPickupItemEvent) {
        if (e.entity !is Player) return
        if (!approved(e.entity as Player)) e.isCancelled = true
    }

    @EventHandler
    fun stopEntityTarget(e: EntityTargetEvent) {
        if (e.target !is Player) return
        if (!approved(e.target as Player, false)) e.isCancelled = true
    }

    @EventHandler
    fun stopAttack(e: EntityDamageByEntityEvent) {
        if (e.damager !is Player) return
        if (!approved(e.damager as Player)) e.isCancelled = true
    }

    @EventHandler
    fun disallowCommands(e: PlayerCommandPreprocessEvent) {
        if (!approved(e.player)) e.isCancelled = true
    }
}
