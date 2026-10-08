package com.bencodez.votingplugin.timequeue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Queue;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.bencodez.advancedcore.api.time.events.DateChangedEvent;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.events.PlayerVoteEvent;

import lombok.Getter;

public class TimeQueueHandler implements Listener {
	@Getter
	private Queue<VoteTimeQueue> timeChangeQueue = new ConcurrentLinkedQueue<>();

	private VotingPluginMain plugin;
	// Only wake-up handles; actual votes remain in the existing persistent queue.
	private final Object checkOwner = new Object();
	private final Set<ScheduledFuture<?>> checks = new HashSet<>();
	private boolean stoppingChecks;


	public TimeQueueHandler(VotingPluginMain plugin) {
		this.plugin = plugin;
		load();
	}

	public void addVote(String voteUsername, String voteSiteName) {
		timeChangeQueue.add(new VoteTimeQueue(voteUsername, voteSiteName,
				LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
	}

	public void load() {
		for (String str : plugin.getServerData().getTimedVoteCacheKeys()) {
			ConfigurationSection data = plugin.getServerData().getTimedVoteCacheSection(str);
			timeChangeQueue
					.add(new VoteTimeQueue(data.getString("Name"), data.getString("Service"), data.getLong("Time")));
		}
		plugin.getServerData().clearTimedVoteCache();
		scheduleCheck(120);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void postTimeChange(DateChangedEvent event) {
		scheduleCheck(5);
	}

	private void scheduleCheck(long delaySeconds) {
		synchronized (checkOwner) {
			if (stoppingChecks) return;
			checks.removeIf(java.util.concurrent.Future::isDone);
			final ScheduledFuture<?>[] handle = new ScheduledFuture<?>[1];
			handle[0] = plugin.getVoteTimer().schedule(() -> {
				synchronized (checkOwner) { if (stoppingChecks) return; }
				try { processQueue(); }
				finally { synchronized (checkOwner) { checks.remove(handle[0]); } }
			}, delaySeconds, TimeUnit.SECONDS);
			checks.add(handle[0]);
		}
	}

	/** Cancel delayed queue wake-ups, preserving queued votes and any running vote body. */
	public void stopScheduledChecks() {
		synchronized (checkOwner) {
			stoppingChecks = true;
			for (ScheduledFuture<?> check : checks) check.cancel(false);
			checks.clear();
		}
	}

	public void processQueue() {
		while (getTimeChangeQueue().size() > 0) {
			VoteTimeQueue vote = getTimeChangeQueue().remove();
			PlayerVoteEvent voteEvent = new PlayerVoteEvent(
					plugin.getVoteSite(plugin.getVoteSiteName(true, vote.getService()), true), vote.getName(),
					vote.getService(), true);
			voteEvent.setTime(voteEvent.getTime());
			plugin.getServer().getPluginManager().callEvent(voteEvent);

			if (voteEvent.isCancelled()) {
				plugin.debug("Vote cancelled");
				return;
			}
		}
	}

	public void save() {
		if (!timeChangeQueue.isEmpty()) {
			int num = 0;
			for (VoteTimeQueue vote : timeChangeQueue) {
				plugin.getServerData().addTimeVoted(num, vote);
				num++;
			}
		}
		timeChangeQueue.clear();
	}
}
