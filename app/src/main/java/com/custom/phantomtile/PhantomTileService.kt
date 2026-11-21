package com.custom.phantomtile

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

/**
 * Quick Settings tile that toggles the Android "phantom process killer"
 * (settings_enable_monitor_phantom_procs) via root.
 *
 * - settings value "false"  -> phantom processes are NOT limited (tile ACTIVE)
 * - settings value "true"/null/other -> default limiting (tile INACTIVE)
 *
 * Zero dependencies, no background service, no launcher icon.
 */
class PhantomTileService : TileService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Last state confirmed from the system (true = limiting disabled). */
    @Volatile
    private var lastKnownDisabled = false

    override fun onTileAdded() {
        super.onTileAdded()
        syncTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        // 1. Optimistic UI update: instant native QS animation, no waiting for su.
        val targetDisabled = !lastKnownDisabled
        applyState(targetDisabled)

        // 2. Run the root commands off the main thread.
        Thread {
            val ok = runRootCommands(
                if (targetDisabled) DISABLE_COMMANDS else ENABLE_COMMANDS
            )
            mainHandler.post {
                if (ok) {
                    // 3. Re-read the real system state and calibrate the tile.
                    syncTileState()
                } else {
                    // Root denied or command failed: revert and tell the user.
                    applyState(false)
                    Toast.makeText(applicationContext, "Root 授权失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    /** Reads the real system value on a worker thread, then applies it on main. */
    private fun syncTileState() {
        Thread {
            val disabled = readPhantomDisabled() ?: false
            mainHandler.post { applyState(disabled) }
        }.start()
    }

    private fun applyState(disabled: Boolean) {
        lastKnownDisabled = disabled
        val tile = qsTile ?: return
        if (disabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "不限制子进程"
            tile.subtitle = "已停止限制"
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "子进程限制"
            tile.subtitle = "默认限制"
        }
        tile.updateTile()
    }

    /**
     * Returns true when phantom-process limiting is disabled
     * (settings value is exactly "false"), false otherwise, null on error.
     */
    private fun readPhantomDisabled(): Boolean? {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("su", "-c", "settings get global settings_enable_monitor_phantom_procs")
            )
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            output == "false"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Runs all commands through a single su process (stdin pipe).
     * Returns true when the su session exits with code 0.
     */
    private fun runRootCommands(commands: List<String>): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.bufferedWriter().use { writer ->
                for (cmd in commands) {
                    writer.write(cmd)
                    writer.newLine()
                }
                writer.write("exit")
                writer.newLine()
                writer.flush()
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val DISABLE_COMMANDS = listOf(
            "/system/bin/device_config set_sync_disabled_for_tests persistent",
            "/system/bin/device_config put activity_manager max_phantom_processes 2147483647",
            "settings put global settings_enable_monitor_phantom_procs false"
        )
        private val ENABLE_COMMANDS = listOf(
            "/system/bin/device_config set_sync_disabled_for_tests none",
            "/system/bin/device_config delete activity_manager max_phantom_processes",
            "settings put global settings_enable_monitor_phantom_procs true"
        )
    }
}
