package com.Ben12345rocks.VotingPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import net.milkbowl.vault.economy.Economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.Ben12345rocks.VotingPlugin.Commands.CommandLoader;
import com.Ben12345rocks.VotingPlugin.Commands.Commands;
import com.Ben12345rocks.VotingPlugin.Commands.Executers.CommandAdminVote;
import com.Ben12345rocks.VotingPlugin.Commands.Executers.CommandVote;
import com.Ben12345rocks.VotingPlugin.Commands.TabCompleter.AdminVoteTabCompleter;
import com.Ben12345rocks.VotingPlugin.Commands.TabCompleter.VoteTabCompleter;
import com.Ben12345rocks.VotingPlugin.Config.Config;
import com.Ben12345rocks.VotingPlugin.Config.ConfigBungeeVoting;
import com.Ben12345rocks.VotingPlugin.Config.ConfigFormat;
import com.Ben12345rocks.VotingPlugin.Config.ConfigGUI;
import com.Ben12345rocks.VotingPlugin.Config.ConfigOtherRewards;
import com.Ben12345rocks.VotingPlugin.Config.ConfigRewards;
import com.Ben12345rocks.VotingPlugin.Config.ConfigTopVoterAwards;
import com.Ben12345rocks.VotingPlugin.Config.ConfigVoteReminding;
import com.Ben12345rocks.VotingPlugin.Config.ConfigVoteSites;
import com.Ben12345rocks.VotingPlugin.Data.ServerData;
import com.Ben12345rocks.VotingPlugin.Events.BlockBreak;
import com.Ben12345rocks.VotingPlugin.Events.PlayerInteract;
import com.Ben12345rocks.VotingPlugin.Events.PlayerJoinEvent;
import com.Ben12345rocks.VotingPlugin.Events.SignChange;
import com.Ben12345rocks.VotingPlugin.Events.VotiferEvent;
import com.Ben12345rocks.VotingPlugin.Files.Files;
import com.Ben12345rocks.VotingPlugin.Metrics.Metrics;
import com.Ben12345rocks.VotingPlugin.Objects.CommandHandler;
import com.Ben12345rocks.VotingPlugin.Objects.Reward;
import com.Ben12345rocks.VotingPlugin.Objects.SignHandler;
import com.Ben12345rocks.VotingPlugin.Objects.UUID;
import com.Ben12345rocks.VotingPlugin.Objects.User;
import com.Ben12345rocks.VotingPlugin.Objects.VoteSite;
import com.Ben12345rocks.VotingPlugin.Signs.Signs;
import com.Ben12345rocks.VotingPlugin.TopVoter.TopVoter;
import com.Ben12345rocks.VotingPlugin.Updater.CheckUpdate;
import com.Ben12345rocks.VotingPlugin.Updater.Updater;
import com.Ben12345rocks.VotingPlugin.VoteReminding.VoteReminding;

public class Main extends JavaPlugin {

	public static Config config;

	public static ConfigOtherRewards configBonusReward;

	public static ConfigGUI configGUI;

	public static ConfigFormat configFormat;

	public static ConfigVoteSites configVoteSites;

	public Economy econ = null;

	public static Main plugin;

	public HashMap<User, Integer> topVoterMonthly;

	public HashMap<User, Integer> topVoterWeekly;

	public HashMap<User, Integer> topVoterDaily;

	public Updater updater;

	public ArrayList<CommandHandler> voteCommand;

	public ArrayList<CommandHandler> adminVoteCommand;

	public ArrayList<VoteSite> voteSites;

	public HashMap<User, HashMap<VoteSite, Date>> voteToday;

	public boolean placeHolderAPIEnabled;

	public ArrayList<Reward> rewards;

	public boolean titleAPIEnabled;

	public ArrayList<SignHandler> signs;

	public void checkPlaceHolderAPI() {
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			placeHolderAPIEnabled = true;
			plugin.debug("PlaceholderAPI found, will attempt to parse placeholders");
		} else {
			placeHolderAPIEnabled = false;
			plugin.debug("PlaceholderAPI not found, PlaceholderAPI placeholders will not work");
		}
	}

	public void checkTitleAPI() {
		if (Bukkit.getPluginManager().getPlugin("TitleAPI") != null) {
			titleAPIEnabled = true;
			plugin.debug("Found TitleAPI, will attempt to send titles");
		} else {
			titleAPIEnabled = false;
			plugin.debug("TitleAPI not found, titles will not send");
		}
	}

	private void checkVotifier() {
		if (getServer().getPluginManager().getPlugin("Votifier") == null) {
			plugin.debug("Votifier not found, votes may not work");
		}
	}

	public void debug(String message) {
		if (config.getDebugEnabled()) {
			plugin.getLogger().info("Debug: " + message);
		}
	}

	public User getUser(String playerName) {
		return new User(playerName);
	}

	public User getUser(UUID uuid) {
		return new User(uuid);
	}

	public VoteSite getVoteSite(String siteName) {
		for (VoteSite voteSite : voteSites) {
			if (voteSite.getSiteName().equalsIgnoreCase(siteName)) {
				return voteSite;
			}
		}
		if (config.getAutoCreateVoteSites()) {
			configVoteSites.generateVoteSite(siteName);
			return new VoteSite(siteName);
		} else {
			return null;
		}
	}

	public void loadRewards() {
		ConfigRewards.getInstance().setupExample();
		rewards = new ArrayList<Reward>();
		for (String reward : ConfigRewards.getInstance().getRewardNames()) {
			rewards.add(new Reward(reward));
		}
		plugin.debug("Loaded rewards");

	}

	public void loadVoteSites() {
		configVoteSites.setup("ExampleVoteSite");
		voteSites = configVoteSites.getVoteSitesLoad();

		plugin.debug("Loaded VoteSites");

	}

	private void metrics() {
		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
			plugin.debug("Loaded Metrics");
		} catch (IOException e) {
			plugin.getLogger().info("Can't submit metrics stats");
		}
	}

	@Override
	public void onDisable() {
		Signs.getInstance().storeSigns();
		plugin = null;
	}

	@Override
	public void onEnable() {
		plugin = this;
		Files.getInstance().loadFileEditngThread();
		setupFiles();
		registerCommands();
		registerEvents();
		if (setupEconomy()) {
			plugin.debug("Succesfully hooked into vault");
		} else {
			plugin.getLogger()
					.info("Failed to load vault, giving players money directy will not work");
		}
		checkVotifier();
		metrics();

		CheckUpdate.getInstance().startUp();

		checkPlaceHolderAPI();
		checkTitleAPI();

		loadVoteSites();
		loadRewards();

		VoteReminding.getInstance().loadRemindChecking();

		Bukkit.getScheduler().runTask(plugin, new Runnable() {

			@Override
			public void run() {
				Signs.getInstance().loadSigns();
			}
		});

		topVoterMonthly = new HashMap<User, Integer>();
		topVoterWeekly = new HashMap<User, Integer>();
		topVoterDaily = new HashMap<User, Integer>();
		voteToday = new HashMap<User, HashMap<VoteSite, Date>>();
		startTimer();
		plugin.getLogger().info(
				"Enabled VotingPlgin " + plugin.getDescription().getVersion());
	}

	private void registerCommands() {
		CommandLoader.getInstance().loadCommands();
		CommandLoader.getInstance().loadAliases();

		// /vote, /v
		getCommand("vote").setExecutor(new CommandVote(this));
		getCommand("vote").setTabCompleter(new VoteTabCompleter());
		getCommand("v").setExecutor(new CommandVote(this));
		getCommand("v").setTabCompleter(new VoteTabCompleter());

		// /adminvote, /av
		getCommand("adminvote").setExecutor(new CommandAdminVote(this));
		getCommand("adminvote").setTabCompleter(new AdminVoteTabCompleter());
		getCommand("av").setExecutor(new CommandAdminVote(this));
		getCommand("av").setTabCompleter(new AdminVoteTabCompleter());

		plugin.debug("Loaded Commands");

	}

	private void registerEvents() {
		PluginManager pm = getServer().getPluginManager();

		pm.registerEvents(new PlayerJoinEvent(this), this);
		pm.registerEvents(new VotiferEvent(this), this);

		pm.registerEvents(new SignChange(this), this);

		pm.registerEvents(new BlockBreak(this), this);

		pm.registerEvents(new PlayerInteract(this), this);

		plugin.debug("Loaded Events");

	}

	public void reload() {
		config.reloadData();
		configGUI.reloadData();
		configFormat.reloadData();
		plugin.loadVoteSites();
		configBonusReward.reloadData();
		ConfigVoteReminding.getInstance().reloadData();
		plugin.setupFiles();
		loadRewards();
		ServerData.getInstance().reloadData();
		plugin.update();
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer()
				.getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}

	public void setupFiles() {
		config = Config.getInstance();
		configVoteSites = ConfigVoteSites.getInstance();
		configFormat = ConfigFormat.getInstance();
		configBonusReward = ConfigOtherRewards.getInstance();
		configGUI = ConfigGUI.getInstance();

		config.setup(this);
		configFormat.setup(this);
		configBonusReward.setup(this);
		configGUI.setup(plugin);
		ConfigVoteReminding.getInstance().setup(plugin);

		ConfigBungeeVoting.getInstance().setup(plugin);

		ServerData.getInstance().setup(plugin);

		ConfigTopVoterAwards.getInstance().setup(plugin);

		plugin.debug("Loaded Files");

	}

	public void startTimer() {
		Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
				new Runnable() {

					@Override
					public void run() {
						update();
					}
				}, 50, config.getBackgroundTaskDelay() * 20);

		plugin.debug("Loaded timer for background task");

	}

	public void update() {
		try {
			TopVoter.getInstance().updateTopVoters();
			updater = new Updater(this, 15358, false);
			Commands.getInstance().updateVoteToday();
			TopVoter.getInstance().checkTopVoterAward();
			ServerData.getInstance().updateValues();
			Signs.getInstance().updateSigns();
			for (Player player : Bukkit.getOnlinePlayers()) {
				new User(player).offVoteWorld(player.getWorld().getName());
			}
			plugin.debug("Background task ran");

		} catch (Exception ex) {
			plugin.getLogger()
					.info("Looks like there are no data files or something went wrong.");
			ex.printStackTrace();
		}
	}

}
