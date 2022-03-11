package org.mineacademy.fo.model;

import lombok.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.collection.StrictList;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.Remain;

import java.util.*;

public class SimpleScoreboard {

	// ------------------------------------------------------------------------------------------------------------
	// Static
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Fixes problems with Java character conversation (toString())
	 */
	private static final String COLOR_CHAR = "\u00A7";

	/**
	 * Unique chatcolor identifiers for specific team entries
	 */
	private static final Map<Integer, String> lineEntries;

	static {
		lineEntries = new HashMap<>();

		for(int i = 0; i < 10; i++)
			lineEntries.put(i, COLOR_CHAR + i + COLOR_CHAR + "r");

		lineEntries.put(10, COLOR_CHAR + "a" + COLOR_CHAR + "r");
		lineEntries.put(11, COLOR_CHAR + "b" + COLOR_CHAR + "r");
		lineEntries.put(12, COLOR_CHAR + "c" + COLOR_CHAR + "r");
		lineEntries.put(13, COLOR_CHAR + "d" + COLOR_CHAR + "r");
		lineEntries.put(14, COLOR_CHAR + "e" + COLOR_CHAR + "r");
		lineEntries.put(15, COLOR_CHAR + "f" + COLOR_CHAR + "r");
	}

	/**
	 * List of all active scoreboard (added upon creating a new instance)
	 */
	@Getter
	private final static List<SimpleScoreboard> registeredBoards = new ArrayList<>();

	/**
	 * Clears registered boards, usually called on reload
	 */
	public static void clearBoards() {
		registeredBoards.clear();
	}

	/**
	 * Removes all scoreboard for a player
	 *
	 * @param player
	 */
	public static void clearBoardsFor(final Player player) {
		for (final SimpleScoreboard scoreboard : registeredBoards)
			if (scoreboard.isViewing(player))
				scoreboard.hide(player);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Public entries
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Stored scoreboard lines
	 */
	@Getter
	private final List<String> rows = new ArrayList<>();

	/**
	 * A list of viewed scoreboards
	 */
	private final StrictList<ViewedScoreboard> scoreboards = new StrictList<>();

	/**
	 * The color theme for key: value pairs such as
	 * <p>
	 * Players: 12
	 * Mode: playing
	 */
	private final String[] theme = new String[2];

	/**
	 * The title of this scoreboard
	 */
	@Getter
	private String title;

	/**
	 * The update tick delay
	 */
	@Getter
	private int updateDelayTicks;

	// ------------------------------------------------------------------------------------------------------------
	// Classes
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Stores a viewed scoreboard per player
	 */
	@Getter
	@Setter
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	private static class ViewedScoreboard {

		/**
		 * The scoreboard
		 */
		private final Scoreboard scoreboard;

		/**
		 * The viewer
		 */
		private final Player viewer;

		@Override
		public boolean equals(final Object obj) {
			return obj instanceof ViewedScoreboard && ((ViewedScoreboard) obj).getViewer().equals(this.viewer);
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Private entries
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * The running update task
	 */
	private BukkitTask updateTask;

	/**
	 * Create a new scoreboard
	 */
	public SimpleScoreboard() {
		registeredBoards.add(this);
	}

	// ------------------------------------------------------------------------------------------------------------
	// Add new rows
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Add rows onto the scoreboard
	 *
	 * @param entries
	 */
	public final void addRows(final String... entries) {
		addRows(Arrays.asList(entries));
	}

	/**
	 * Add rows onto the scoreboard
	 *
	 * @param entries
	 */
	public final void addRows(final List<String> entries) {
		rows.addAll(entries);
	}

	/**
	 * Remove all rows
	 */
	public final void clearRows() {
		rows.clear();
	}

	/**
	 * Remove row at the given index
	 *
	 * @param index
	 */
	public final void removeRow(final int index) {
		rows.remove(index);
	}

	/**
	 * Remove row that contains the given text
	 *
	 * @param thatContains
	 */
	public final void removeRow(final String thatContains) {
		rows.removeIf(row -> row.contains(thatContains));
	}

	// ------------------------------------------------------------------------------------------------------------
	// Start / stop
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Starts visualizing this scoreboard
	 */
	private void start() {
		Valid.checkBoolean(updateTask == null, "Scoreboard " + this + " already running");

		updateTask = new BukkitRunnable() {
			@Override
			public void run() {

				try {
					update();

				} catch (final Throwable t) {
					final String lines = String.join(" ", rows);

					Common.error(t,
							"Error displaying " + SimpleScoreboard.this,
							"Entries: " + lines,
							"%error",
							"Stopping rendering for safety.");

					stop();
				}
			}
		}.runTaskTimer(SimplePlugin.getInstance(), 0, updateDelayTicks);
	}

	/**
	 * Updates this scoreboard
	 */
	private void update() {
		onUpdate();

		for (final ViewedScoreboard viewedScoreboard : scoreboards)
			reloadEntries(viewedScoreboard);
	}

	/**
	 * Reload entries for the given player
	 *
	 * @param viewedScoreboard
	 */
	private void reloadEntries(final ViewedScoreboard viewedScoreboard) {
		int size = Math.min(rows.size(), 16);
		Scoreboard scoreboard = viewedScoreboard.getScoreboard();
		Objective mainboard = scoreboard.getObjective("mainboard");

		if (mainboard == null) {
			mainboard = scoreboard.registerNewObjective("mainboard", "dummy"); //1.8 hasn't have the method with the displayname
			mainboard.setDisplayName(title);
			mainboard.setDisplaySlot(DisplaySlot.SIDEBAR);
		}

		if (!mainboard.getDisplayName().equals(title))
			mainboard.setDisplayName(title);

		for (int lineNumber = 0; lineNumber < 16; lineNumber++) {
			int scoreboardLine = size - lineNumber;
			String teamEntry = lineEntries.get(lineNumber);
			Team line = scoreboard.getTeam("line" + scoreboardLine);

			//31 or 30 is the maximum, depends on the chatcolor from the previous line
			//16 for prefix, COLOR_CHAR + chatcolor and 14 more characters
			//Maybe there is a way to get more characters, but then you have to play with the entries
			//(Team.addEntry() and add the same entry on the scoreboard)
			//Refer to https://www.spigotmc.org/wiki/making-scoreboard-with-teams-no-flicker/ for more information

			if (lineNumber < rows.size()) {
				String sidebarEntry = rows.get(lineNumber);
				String text = replaceTheme(replaceVariables(viewedScoreboard.getViewer(), sidebarEntry));
				String prefix = text.substring(0, Math.min(16, text.length()));
				//Copying over the chatcolor from the prefix
				String suffix = text.length() > 16 ? text.substring(16, Math.min(prefix.endsWith(COLOR_CHAR) ? 31 : 30, text.length())) : "";

				if (prefix.endsWith(COLOR_CHAR)) {
					prefix = prefix.substring(0, prefix.length() - 1);
					suffix = COLOR_CHAR + suffix;
				} else {
					suffix = ChatColor.getLastColors(prefix) + suffix;
				}

				if (line == null)
					line = scoreboard.registerNewTeam("line" + scoreboardLine);

				if (!line.getPrefix().equals(prefix))
					line.setPrefix(prefix);

				if (!line.getSuffix().equals(suffix))
					line.setSuffix(suffix);

				if (!line.getEntries().contains(teamEntry))
					line.addEntry(teamEntry);

				Remain.getScore(mainboard, teamEntry).setScore(scoreboardLine);
			} else {
				//If the line is removed, you have to unregister the team
				scoreboard.resetScores(teamEntry);

				if (line != null)
					line.unregister();
			}
		}
	}

	/**
	 * Adds theme colors to the row if applicable
	 *
	 * @param row
	 * @return
	 */
	private String replaceTheme(final String row) {
		if (row.contains(":"))
			if (theme.length == 1)
				return theme[0] + row;

			else if (theme[0] != null) {
				final String[] split = row.split("\\:");

				if (split.length > 1)
					return theme[0] + split[0] + ":" + theme[1] + split[1];
			}

		return row;
	}

	/**
	 * Set the coloring theme for rows having : such as
	 * <p>
	 * Players: 12
	 * Mode: playing
	 * <p>
	 * To use simply put color codes
	 *
	 * @param primary
	 * @param secondary
	 */
	public final void setTheme(@NonNull final ChatColor primary, final ChatColor secondary) {
		if (secondary != null) {
			this.theme[0] = "&" + primary.getChar();
			this.theme[1] = "&" + secondary.getChar();
		} else
			this.theme[0] = "&" + primary.getChar();
	}

	/**
	 * Replaces variables in the message for the given player
	 *
	 * @param player
	 * @param message
	 * @return
	 */
	protected @NotNull String replaceVariables(final @NonNull Player player, final @NonNull String message) {
		return message;
	}

	/**
	 * Called when this scoreboard is ticked
	 */
	protected void onUpdate() {
	}

	/**
	 * Stops this scoreboard and removes it from all viewers
	 */
	public final void stop() {
		for (final Iterator<ViewedScoreboard> iterator = scoreboards.iterator(); iterator.hasNext(); ) {
			final ViewedScoreboard score = iterator.next();

			score.getViewer().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
			iterator.remove();
		}

		if (updateTask != null)
			cancelUpdateTask();
	}

	/**
	 * Cancels the update task
	 */
	private void cancelUpdateTask() {
		Valid.checkNotNull(updateTask, "Scoreboard " + this + " not running");

		updateTask.cancel();
		updateTask = null;
	}

	/**
	 * Return true if the scoreboard is running and rendering?
	 *
	 * @return
	 */
	public final boolean isRunning() {
		return updateTask != null;
	}

	/**
	 * @param updateDelayTicks the updateDelayTicks to set
	 */
	public final void setUpdateDelayTicks(int updateDelayTicks) {
		this.updateDelayTicks = updateDelayTicks;
	}

	/**
	 * @param title the title to set
	 */
	public final void setTitle(String title) {
		this.title = title;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Show this scoreboard to the player
	 *
	 * @param player
	 */
	public final void show(final Player player) {
		Valid.checkBoolean(!isViewing(player), "Player " + player.getName() + " is already viewing scoreboard: " + getTitle());

		if (updateTask == null)
			start();

		final Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();

		scoreboards.add(new ViewedScoreboard(scoreboard, player));
		player.setScoreboard(scoreboard);
	}

	/**
	 * Hide this scoreboard from the player
	 *
	 * @param player
	 */
	public final void hide(final Player player) {
		Valid.checkBoolean(isViewing(player), "Player " + player.getName() + " is not viewing scoreboard: " + getTitle());

		player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

		for (final ViewedScoreboard viewed : scoreboards)
			if (viewed.getViewer().equals(player)) {
				scoreboards.remove(viewed);
				break;
			}

		if (scoreboards.isEmpty())
			cancelUpdateTask();
	}

	/**
	 * Returns true if the given player is viewing the scoreboard
	 *
	 * @param player
	 * @return
	 */
	public final boolean isViewing(final Player player) {
		for (final ViewedScoreboard viewed : scoreboards)
			if (viewed.getViewer().equals(player))
				return true;

		return false;
	}

	@Override
	public final String toString() {
		return "Scoreboard{title=" + getTitle() + "}";
	}
}