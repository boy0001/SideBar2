package com.empcraft.sidebar;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

public final class SideBar extends JavaPlugin implements Listener {
	public static String version = null;
	static ScriptEngine engine = (new ScriptEngineManager()).getEngineByName("JavaScript");
	volatile Map<String, ArrayList<OfflinePlayer>> removeScores = new ConcurrentHashMap<String, ArrayList<OfflinePlayer>>();
	volatile Map<String, Placeholder> placeholders = new ConcurrentHashMap<String, Placeholder>();
	volatile Map<String, Placeholder> defaultplaceholders = new ConcurrentHashMap<String, Placeholder>();
	volatile Map<String, SimpleScoreboard> scoreboards = new ConcurrentHashMap<String, SimpleScoreboard>();
	volatile Map<String, SimpleScoreboard> simpleScoreboards = new ConcurrentHashMap<String, SimpleScoreboard>();
	volatile Map<String, Object> globals = new ConcurrentHashMap<String, Object>();
	private volatile Player currentplayer = null;
	private volatile Player currentsender = null;
	static SideBar plugin;
	Plugin individualmessages = null;
	int recursion = 0;
	int counter = 0;
	Timer timer = new Timer();
	TimerTask mytask = new TimerTask() {
		@Override
		public void run() {
			counter++;
			for (final Player player : getServer().getOnlinePlayers()) {
				setUser(player);
				setSender(player);
				final ScoreboardManager manager = Bukkit.getScoreboardManager();
				if (!getConfig().getBoolean("compatibility-mode")) {
					player.setScoreboard(manager.getNewScoreboard());
				}
				Scoreboard sidebar = player.getScoreboard();
				Objective obj;
				if (sidebar==null) {
					player.setScoreboard(manager.getNewScoreboard());
					sidebar = getServer().getScoreboardManager().getNewScoreboard();
					obj = sidebar.registerNewObjective("ad", "dummy");
					obj.setDisplaySlot(DisplaySlot.SIDEBAR);
					player.setScoreboard(sidebar);
				}
				if (sidebar.getObjective(DisplaySlot.SIDEBAR)==null || sidebar.getObjective(DisplaySlot.SIDEBAR).getScoreboard()!=sidebar) {
					obj = sidebar.registerNewObjective("sb", "dummy");
					obj.setDisplaySlot(DisplaySlot.SIDEBAR);
					obj.setDisplayName(player.getName());
					player.setScoreboard(sidebar);
				}
				if (checkperm(player, "sidebar.use")) {
					if (scoreboards.containsKey(player.getName())) {
						SimpleScoreboard scoreboard = scoreboards.get(player.getName());
						if (checkperm(player, scoreboard.getPermission())) {
							obj = sidebar.getObjective(DisplaySlot.SIDEBAR);
							if (getConfig().getBoolean("compatibility-mode")) {
								if (removeScores.containsKey(player.getName())) {
									ArrayList<OfflinePlayer> list = removeScores.get(player.getName());
									for(OfflinePlayer offlinePlayer : list) {
										sidebar.resetScores(offlinePlayer);
									}
									removeScores.remove(player.getName());
								}
							}
							String title = colorise(evaluate(scoreboard.getTitle(),null,null));
							title.substring(0,Math.min(title.length(), 15));
							obj.setDisplayName(title);
							Map<String, String> scores = scoreboard.getScores();
							ArrayList<OfflinePlayer> toRemove = new ArrayList<OfflinePlayer>();
							for (String score:scores.keySet()) {
								String key = score;
								String result = evaluate(scores.get(score),null,null);
								int value = -1;
								try {
									value = Integer.parseInt(result);
								}
								catch (Exception e) { msg(null,"Error casting score to int \""+result+"\" in scoreboard \""+title+"\"");}
								score = colorise(evaluate(score, null, null));
								score = score.substring(0,Math.min(score.length(), 15));
								if (key.contains("{")) {
									toRemove.add(Bukkit.getOfflinePlayer(score));
								}
								Score myscore = obj.getScore(Bukkit.getOfflinePlayer(score));
								myscore.setScore(value);
							}
							if (toRemove.size()>0) {
								removeScores.put(player.getName(), toRemove);
							}
							player.setScoreboard(sidebar);
						}
					}
					
				}
			}
			setUser(null);
			setSender(null);
			if (counter > 1200000/(50*getConfig().getLong("sidebar.autoupdate.interval-ticks"))) {
				counter = 0;
				try {
					msg(null, "&8===&a[&7SideBar&a]&8===");
					msg(null, "Saving variables...");
					getConfig().getConfigurationSection("scripting").set("variables", null);
					for (final Entry<String, Object> node : globals.entrySet()) {
						getConfig().options().copyDefaults(true);
						getConfig().set("scripting.variables." + ("" + node.getKey()).substring(1, ("" + node.getKey()).length() - 1), ("" + node.getValue()));
						saveConfig();
					}
				} catch (final Exception e) {
				}
			}
		}
	};

	void addgvar(final String key, final String value) {
		globals.put(key, value);
	}

	public synchronized void addPlaceholder(final Placeholder placeholder) {
		defaultplaceholders.put(placeholder.getKey(), placeholder);
		placeholders.put(placeholder.getKey(), placeholder);
	}

	public synchronized boolean addPlaceholder(final String key) {
		final Placeholder placeholder = defaultplaceholders.get(key);
		if (placeholder != null) {
			placeholders.put(placeholder.getKey(), placeholder);
			return true;
		}
		return false;
	}

	boolean checkperm(final Player player, final String perm) {
		boolean hasperm = false;
		final String[] nodes = perm.split("\\.");
		String node = "";
		if (player == null) {
			return true;
		} else if (player.hasPermission(perm)) {
			hasperm = true;
		} else if (player.isOp() == true) {
			hasperm = true;
		} else {
			for (int i = 0; i < (nodes.length - 1); i++) {
				node += nodes[i] + ".";
				if (player.hasPermission(node + "*")) {
					hasperm = true;
				}
			}
		}
		return hasperm;
	}

	String colorise(final String mystring) {
		return ChatColor.translateAlternateColorCodes('&', mystring);
	}

	void delgvar(final String key) {
		globals.remove(key);
	}

	public String evaluate(String line, final Boolean elevation, final Location interact) {
		try {
			int q = 0;
			final List<Integer> indicies = new ArrayList<Integer>();
			for (int i = 0; i < line.length(); i++) {
				final char current = line.charAt(i);
				if (current == '{') {
					indicies.add(i);
					q++;
				} else if (current == '}') {
					if (q > 0) {
						if (recursion < 513) {
							q--;
							final int lastindx = indicies.size() - 1;
							final int start = indicies.get(lastindx);
							String result;
							try {
								result = fphs(line.substring(start, i + 1), elevation, interact);
							} catch (final Exception e) {
								result = "null";
								msg(null, "ERR(syntax) Invalid Placeholder: " + line.substring(start, i + 1));
							}
							line = (new StringBuffer(line).replace(start, i + 1, result)).toString();
							indicies.remove(lastindx);
							i = start;
						} else {
							msg(null, "ERR(recursion): " + line);
							msg(null, "^ placeholder returns another placeholder");
						}
					}
				}
			}
			if (line.contains(",") == false) {
				if (line.matches(".*\\d.*")) {
					boolean num = false;
					if (line.contains("+")) {
						num = true;
					} else if (line.contains("-")) {
						num = true;
					} else if (line.contains("*")) {
						num = true;
					} else if (line.contains("/")) {
						num = true;
					} else if (line.contains("%")) {
						num = true;
					} else if (line.contains("=")) {
						num = true;
					} else if (line.contains(">")) {
						num = true;
					} else if (line.contains("<")) {
						num = true;
					} else if (line.contains("|")) {
						num = true;
					} else if (line.contains("&")) {
						num = true;
					}
					if (num) {
						line = javascript(line);
					}
				}
			}
			if (line.equals("null")) {
				return "";
			}
			return line;
		} catch (final Exception e2) {
			e2.printStackTrace();
			return "";
		}
	}

	public String execute(final String line, final Boolean elevation, final Location interact) {
		Player user = getUser();
		recursion++;
		try {
			final Map<String, Object> locals = new HashMap<String, Object>();
			locals.put("{var}", StringUtils.join(locals.keySet(), ",").replace("{", "").replace("}", ""));
			final String[] mycmds = line.split(";");
			boolean hasperm = true;
			int depth = 0;
			int last = 0;
			int i2 = 0;
			String myvar = "null";
			for (int i = 0; i < mycmds.length; i++) {
				if (i >= i2) {
					String mycommand = evaluate(mycmds[i], elevation, interact);
					for (final Entry<String, Object> node : locals.entrySet()) {
						if (mycommand.contains(node.getKey())) {
							mycommand = mycommand.replace(node.getKey(), (CharSequence) node.getValue());
						}
					}
					if ((mycommand.equals("") || mycommand.equals("null")) == false) {
						final String[] cmdargs = mycommand.split(" ");

						if (cmdargs[0].trim().equalsIgnoreCase("for")) {
							if (hasperm) {
								int mylength = 0;
								int mode = 0;
								String mytest = "";
								int depth2 = 1;
								int j = 0;
								for (j = i + 1; j < mycmds.length; j++) {
									if (mycmds[j].split(" ")[0].trim().equals("for")) {
										depth2 += 1;
									} else if (mycmds[j].split(" ")[0].trim().equals("endloop")) {
										depth2 -= 1;
									}
									if (depth2 > 0) {
										mytest += mycmds[j] + ";";
									} else {
									}
									if ((depth2 == 0) || (j == (mycmds.length - 1))) {
										if (cmdargs[1].contains(":")) {
											try {
												mylength = Integer.parseInt(cmdargs[1].split(":")[1].trim());
											} catch (final Exception e) {
												mylength = cmdargs[1].split(":")[1].split(",").length;
												mode = 1;
											}
										} else {
											try {

												mylength = Integer.parseInt(cmdargs[1].trim());
											} catch (final Exception e) {
												mylength = 0;
											}
										}
										if (mode == 1) {
											myvar = "{" + cmdargs[1].split(":")[0] + "}," + globals.get("{" + cmdargs[1].split(":")[0] + "}");
										}
										if (mylength > 512) {
											mylength = 512;
										}
										break;
									}
								}
								for (int k = 0; k < mylength; k++) {
									if (mode == 1) {
										globals.put("{" + cmdargs[1].split(":")[0] + "}", cmdargs[1].split(":")[1].split(",")[k]);
									}
									if (recursion < 512) {
										execute(mytest, elevation, interact);
									}
								}
								if (mode == 1) {
									if (myvar.split(",")[1].equals("null")) {
										globals.remove("{" + cmdargs[1].split(":")[0] + "}");
									} else {
										globals.put("{" + cmdargs[1].split(":")[0] + "}", myvar.split(",")[1]);
									}
								}
								i2 = j + 1;
							}
						} else if (cmdargs[0].equalsIgnoreCase("setuser")) {
							final Player lastuser = user;
							try {
								if (cmdargs[1].equals("null")) {
									user = null;
									setUser(user);
								} else {
									user = Bukkit.getPlayer(cmdargs[1]);
									if (user == null) {
										user = lastuser;
										setUser(user);
									}
								}
							} catch (final Exception e5) {
							}
						} else if (cmdargs[0].equalsIgnoreCase("if")) {
							if (hasperm && (depth == last)) {
								last++;
								hasperm = testif(mycommand, elevation, interact);
							} else {
							}
							depth++;
						} else if (cmdargs[0].equalsIgnoreCase("else")) {
							if (last == depth) {
								if (hasperm) {
									hasperm = false;
								} else {
									hasperm = true;
								}
							}
						} else if (cmdargs[0].equalsIgnoreCase("endif")) {
							if (depth > 0) {
								if (last == depth) {
									hasperm = true;
									if (user != null) {
									}
								}
								if (last == depth) {
									last -= 1;
								}
								depth -= 1;
							} else {
							}
						} else if (cmdargs[0].equalsIgnoreCase("gvar")) {
							if (cmdargs.length > 1) {
								if (cmdargs.length > 2) {
									try {
										globals.put("{" + evaluate(cmdargs[1], elevation, interact) + "}", evaluate(StringUtils.join(Arrays.copyOfRange(cmdargs, 2, cmdargs.length), " "), elevation, interact));
										if (user != null) {
										}
									} catch (final Exception e) {
										if (user != null) {
										}
									}
								} else {
									try {
										globals.remove("{" + cmdargs[1] + "}");
										if (user != null) {
										}
									} catch (final Exception e2) {
										if (user != null) {
										}
									}
								}
							}
						} else if (cmdargs[0].equalsIgnoreCase("var")) {
							if (cmdargs.length > 1) {
								if (cmdargs.length > 2) {
									try {

										locals.put("{" + evaluate(cmdargs[1], elevation, interact) + "}", evaluate(StringUtils.join(Arrays.copyOfRange(cmdargs, 2, cmdargs.length), " "), elevation, interact));
										if (user != null) {
										}
									} catch (final Exception e) {
										if (user != null) {
										}
									}
								} else {
									try {
										locals.remove("{" + cmdargs[1] + "}");
										if (user != null) {
										}
									} catch (final Exception e2) {
										if (user != null) {
										}
									}
								}
							}
						} else if (hasperm) {
							for (final Entry<String, Object> node : locals.entrySet()) {
								if (mycommand.contains(node.getKey())) {
									mycommand = mycommand.replace(node.getKey(), (CharSequence) node.getValue());
								}
							}
							mycommand = mycommand.trim();
							if (mycommand.charAt(0) == '\\') {
								mycommand = mycommand.substring(1, mycommand.length());
								if (user != null) {
									user.chat(mycommand);
								} else {
									getServer().dispatchCommand(getServer().getConsoleSender(), "say " + mycommand);
								}
							} else if (user != null) {
								if (cmdargs[0].equalsIgnoreCase("do")) {
									mycommand = mycommand.substring(3, mycommand.length());
									if (user.isOp()) {
										Bukkit.dispatchCommand(user, mycommand);
									} else {
										try {
											if (elevation) {
												user.setOp(true);
											}
											Bukkit.dispatchCommand(user, mycommand);
										} catch (final Exception e) {
											e.printStackTrace();
										} finally {
											user.setOp(false);
										}

									}

								} else if (cmdargs[0].equalsIgnoreCase("return")) {
									return mycommand.substring(7, mycommand.length());
								} else {
									msg(user, colorise(evaluate(mycommand, elevation, interact)));
								}
							} else {
								if (cmdargs[0].equalsIgnoreCase("return")) {
									return mycommand.substring(7, mycommand.length());
								} else if (cmdargs[0].equalsIgnoreCase("do")) {
									mycommand = mycommand.substring(3, mycommand.length());
									getServer().dispatchCommand(getServer().getConsoleSender(), mycommand);
								} else {
									msg(null, evaluate(mycommand, elevation, interact));
								}
							}

						}

					}
				}
			}
		} catch (final Exception e2) {
			if (user != null) {
				msg(user, colorise(getmsg("ERROR") + getmsg("ERROR1")) + e2);

			} else {
				msg(null, colorise(getmsg("ERROR")) + e2);
			}
		}
		return "null";
	}

	private String fphs(String line, final Boolean elevation, final Location interact) {
		Player user = getUser();
		final String[] mysplit = line.substring(1, line.length() - 1).split(":");
		if (mysplit.length == 2) {
			if ((Bukkit.getPlayer(mysplit[1]) != null)) {
				user = Bukkit.getPlayer(mysplit[1]);
				line = StringUtils.join(mysplit, ":").replace(":" + mysplit[1], "");
			}
			try {
				if ((Bukkit.getPlayer(UUID.fromString(mysplit[1])) != null)) {
					user = Bukkit.getPlayer(UUID.fromString(mysplit[1]));
					line = StringUtils.join(mysplit, ":").replace(":" + mysplit[1], "");
				}
			} catch (final Exception e4) {
			}
		}
		try {
			String[] modifiers;
			final String key = mysplit[0];
			try {
				modifiers = line.substring(2 + key.length(), line.length() - 1).split(":");
			} catch (final Exception e2) {
				modifiers = new String[0];
			}
			return getPlaceholder(key).getValue(user, interact, modifiers, elevation);
		} catch (final Exception e) {

		}
		for (final Entry<String, Object> node : globals.entrySet()) {
			if (line.equals(node.getKey())) {
				return "" + node.getValue();
			}
		}
		return "null";
	}

	public synchronized List<Placeholder> getAllPlaceholders() {
		return new ArrayList<Placeholder>(defaultplaceholders.values());
	}

	public String getmsg(final String key) {
		final File yamlFile = new File(getDataFolder(), getConfig().getString("language").toLowerCase() + ".yml");
		YamlConfiguration.loadConfiguration(yamlFile);
		try {
			return colorise(YamlConfiguration.loadConfiguration(yamlFile).getString(key));
		} catch (final Exception e) {
			return "";
		}
	}

	public synchronized Placeholder getPlaceholder(final String key) {
		return placeholders.get(key);
	}

	public synchronized List<String> getPlaceholderKeys() {
		return new ArrayList<String>(placeholders.keySet());
	}

	public synchronized List<Placeholder> getPlaceholders() {
		return new ArrayList<Placeholder>(placeholders.values());
	}

	public synchronized List<Placeholder> getPlaceholders(final Plugin myplugin) {
		final ArrayList<Placeholder> toReturn = new ArrayList<Placeholder>();
		for (final Placeholder current : placeholders.values()) {
			if (current.getPlugin() != null) {
				if (current.getPlugin().equals(myplugin)) {
					toReturn.add(current);
				}
			}
		}
		return toReturn;
	}

	public synchronized List<Placeholder> getPlaceholders(final String search) {
		final ArrayList<Placeholder> toReturn = new ArrayList<Placeholder>();
		int index = 0;
		for (final Placeholder current : placeholders.values()) {
			if (current.getKey().equalsIgnoreCase(search)) {
				toReturn.add(0, current);
				index += 1;
			} else if (current.getKey().toLowerCase().contains(search.toLowerCase())) {
				toReturn.add(index, current);
			} else if (current.getDescription().toLowerCase().contains(search.toLowerCase())) {
				toReturn.add(current);
			} else if (current.getPlugin() != null) {
				if (current.getPlugin().getName().equalsIgnoreCase(search)) {
					toReturn.add(0, current);
				}
			}
		}
		return toReturn;
	}

	public synchronized SimpleScoreboard getScoreboard(final Player player) {
		return scoreboards.get(player.getName());
	}

	public synchronized SimpleScoreboard getScoreboard(final String playername) {
		return scoreboards.get(playername);
	}

	public Player getSender() {
		return this.currentsender;
	}

	public Player getUser() {
		return this.currentplayer;
	}

	public String javascript(String line) {
		try {
			Object toreturn;
			if ((line.contains(".js")) && (line.contains(" ") == false)) {
				final File file = new File(getDataFolder() + File.separator + getConfig().getString("scripting.directory") + File.separator + line);
				toreturn = engine.eval(new java.io.FileReader(file));
			} else {
				toreturn = engine.eval(line);
			}
			try {
				final Double num = (Double) toreturn;
				if (Math.ceil(num) == Math.floor(num)) {
					line = Long.toString(Math.round(num));
				} else {
					throw new Exception();
				}
			} catch (final Exception d) {
				try {
					final Long num = (Long) toreturn;
					line = Long.toString(num);
				} catch (final Exception f) {
					try {
						final Integer num = (Integer) toreturn;
						line = Integer.toString(num);
					} catch (final Exception g) {
						try {
							final Float num = (Float) toreturn;
							line = Float.toString(num);
						} catch (final Exception h) {
							try {
								line = "" + toreturn;
							} catch (final Exception i) {
							}
						}
					}
				}
			}
		} catch (final Exception e) {
		}
		return line;
	}

	void msg(final Player player, final String mystring) {
		if (ChatColor.stripColor(mystring).equals("")) {
			return;
		}
		if (player == null) {
			getServer().getConsoleSender().sendMessage(colorise(mystring));
		} else {
			player.sendMessage(colorise(mystring));
		}
	}

	@Override
	public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
		Player player;
		if (sender instanceof Player==false) {
			player = null;
		}
		else {
			player = (Player) sender;
		}
		if (args.length > 0) {
			if (args[0].equalsIgnoreCase("help")) {
				msg(player,"&a[&7SIDEBAR HELP&a]");
				msg(player,"&8 - &a/sidebar help &8- shows this page");
				msg(player,"&8 - &a/sidebar set <scoreboard> &8- sets your scoreboard");
				msg(player,"&8 - &a/sidebar set <scoreboard> <player> &8- sets a user's sidebar");
				msg(player,"&8 - &a/sidebar list &8- lists the sidebars");
				msg(player,"&8 - &a/sidebar reload &8- reloads configuration");
				return true;
			}
			if (args[0].equalsIgnoreCase("set")) {
				if (checkperm(player, "sidebar.set")) {
					if (args.length==1) {
						if (simpleScoreboards.containsKey(args[1])) {
							SimpleScoreboard scoreboard = simpleScoreboards.get(args[1]);
							if (checkperm(player, scoreboard.getPermission())) {
								scoreboards.put(player.getName(), simpleScoreboards.get(args[1]));
							}
							else {
								msg(player,"&7Missing requirement: &c"+scoreboard.getPermission());
							}
						}
						else {
							msg(player,"&7Could not find scoreboard: &c"+args[1]);
						}
					}
					else if (args.length==2) {
						if (checkperm(player, "sidebar.set.other")) {
							if (simpleScoreboards.containsKey(args[1])) {
								if (Bukkit.getPlayer(args[2])!=null) {
									scoreboards.put(args[2], simpleScoreboards.get(args[1]));
								}
								else {
									msg(player,"&7Could not find player: &c"+args[2]);
								}
							}
							else {
								msg(player,"&7Could not find scoreboard: &c"+args[1]);
							}
						}
						else {
							msg(player,"&7Missing requirement: &csidebar.set.other");
						}
					}
					else {
						msg(player,"&7Use: &a/sidebar set <scoreboard>");
					}
				}
				else {
					msg(player,"&7Missing requirement: &csidebar.set");
				}
				return true;
			}
			if (args[0].equalsIgnoreCase("list")) {
				if (checkperm(player, "sidebqar.list")) {
					msg(player,"&a[&7SCOREBOARD LIST&a]");
					for (String key:simpleScoreboards.keySet()) {
						msg(player,"&8 - &a"+key);
					}
				}
				else {
					msg(player,"&7Missing requirement: &csidebar.list");
				}
				return true;
			}
			if (args[0].equalsIgnoreCase("reload")) {
				if (checkperm(player, "sidebqar.reload")) {
					msg(player,"&a[&7RELOADING&a]");
					reloadConfig();
					simpleScoreboards = new ConcurrentHashMap<String, SimpleScoreboard>();
					defaultplaceholders = new ConcurrentHashMap<String, Placeholder>();
					final Map<String, Object> options = new HashMap<String, Object>();
					getConfig().set("version", version);
					options.put("language", "english");
					options.put("compatibility-mode", false);
					options.put("scripting.directory", "scripts");
					options.put("scripting.sidebar-directory", "scripts");
					options.put("scripting.debug-level", 0);
					options.put("sidebar.autoupdate.enabled", true);
					options.put("sidebar.autoupdate.interval-ticks", 1);
					options.put("sidebar.autoupdate.async", "Set to 'true' to have a lower impact on the server - may cause instability");
					final List<String> whitelist = Arrays.asList("grounded", "location", "age", "localtime", "localtime12", "display", "uses", "money", "prefix", "suffix", "group", "x", "y", "z", "lvl", "exhaustion", "health", "exp", "hunger", "air", "maxhealth", "maxair", "gamemode", "direction", "biome", "itemname", "itemid", "itemamount", "durability", "dead", "sleeping", "whitelisted", "operator", "sneaking", "itempickup", "flying", "blocking", "age", "bed", "compass", "spawn", "worldticks", "time", "date", "time12", "epoch", "epochmilli", "epochnano", "online", "worlds", "banlist", "baniplist", "operators", "whitelist", "randchoice", "rand", "elevated", "matchgroup", "matchplayer", "hasperm", "js", "gvar", "config", "passenger", "lastplayed", "gprefix", "gsuffix");
					options.put("sidebar.autoupdate.whitelist", whitelist);
					for(World world : getServer().getWorlds()) {
			        	options.put("multiworld."+world.getName()+".sidebar","default");
			        }
					for (final Entry<String, Object> node : options.entrySet()) {
						if (!getConfig().contains(node.getKey())) {
							getConfig().set(node.getKey(), node.getValue());
						}
					}
					try {
						final Set<String> vars = getConfig().getConfigurationSection("scripting.variables").getKeys(false);
						for (final String current : vars) {
							globals.put("{" + current + "}", getConfig().getString("scripting.variables." + current));
						}
					} catch (final Exception e) {
					}
					saveConfig();
					final File f0 = new File(getDataFolder() + File.separator + "scoreboards");
					final File[] mysb = f0.listFiles();
					for (final File element : mysb) {
						if (element.isFile()) {
							if (element.getName().contains(".yml")) {
								try {
									final FileConfiguration yml = YamlConfiguration.loadConfiguration(element);
									String name = element.getName().substring(0, element.getName().length() - 4);
									String title;
									String permission;
									String description;
									Map<String, String> scores = new HashMap<String, String>();
									if (yml.contains("permission" )) {
										 permission = yml.getString("permission");
									}
									else {
										permission = "sidebar.use";
									}
									if (yml.contains("description" )) {
										description = yml.getString("description");
									}
									else {
										description = "No description available.";
									}
									if (yml.contains("title" )) {
										 title = yml.getString("title");
									}
									else {
										title = name;
									}
									
									List<String> keys = yml.getStringList("keys");
									List<String> values = yml.getStringList("values");
									for (int i = 0;i<keys.size();i++) {
										scores.put(keys.get(i), values.get(i));
									}
									SimpleScoreboard scoreboard = new SimpleScoreboard(title, scores, permission, description);
									simpleScoreboards.put(name, scoreboard);
								}
								catch (Exception e) {
									e.printStackTrace();
								}
							}
						}
					}
					final File f1 = new File(getDataFolder() + File.separator + getConfig().getString("scripting.directory"));
					final File[] myph = f1.listFiles();
					for (final File element : myph) {
						if (element.isFile()) {
							if (element.getName().contains(".yml")) {
								try {
									final FileConfiguration yml = YamlConfiguration.loadConfiguration(element);
									final String name = element.getName().substring(0, element.getName().length() - 4);
									final String lines = StringUtils.join(yml.getStringList("script"), ";");
									final String description;
									if (yml.contains("description")) {
										description = yml.getString("description");
									} else {
										description = "There is currently no description";
									}
									final boolean myelevation;
									final boolean asconsole;
									if (yml.contains("elevation")) {
										if (yml.getString("elevation").equalsIgnoreCase("operator")) {
											myelevation = true;
											asconsole = false;
										} else if (yml.getString("elevation").equalsIgnoreCase("console")) {
											myelevation = true;
											asconsole = true;
										} else {
											myelevation = true;
											asconsole = false;
										}
									} else {
										asconsole = false;
										myelevation = false;
									}
									addPlaceholder(new Placeholder(name) {
										@Override
										public String getDescription() {
											return description;
										}
										@Override
										public String getValue(final Player player, final Location location, final String[] modifiers, final Boolean elevation) {
											if (asconsole) {
												setUser(null);
											}
											String newlines = lines;
											for (int i = 0; i<modifiers.length;i++) {
											    newlines = newlines.replace("{arg"+(i+1)+"}", modifiers[i]);
											}
											final String toreturn = execute(newlines, myelevation, location);
											if (asconsole) {
												setUser(player);
											}
											return toreturn;
										}
									});
								} catch (final Exception e2) {
									msg(null, "&cError with file " + getDataFolder() + "/" + getConfig().getString("scripting.directory") + "/" + element.getName() + ".");
								}
							}
						}
					}
				}
				else {
					msg(player,"&7Missing requirement: &csidebar.reload");
				}
				return true;
			}
		}
		else {
			msg(player,"&7Use: &a/sidebar help");
		}
		return false;
	}

	@Override
	public void onDisable() {
		getConfig().getConfigurationSection("scripting").set("placeholders", null);
		try {
			timer.cancel();
			timer.purge();
		} catch (final Exception e) {
		}
		reloadConfig();
		saveConfig();
		msg(null, "SAVING VARIABLES!");
		try {
			for (final Entry<String, Object> node : globals.entrySet()) {
				getConfig().options().copyDefaults(true);
				getConfig().set("scripting.variables." + ("" + node.getKey()).substring(1, ("" + node.getKey()).length() - 1), ("" + node.getValue()));
				saveConfig();
			}
		} catch (final Exception e) {
		}
		msg(null, "&8===&7[&9SideBar&7]&8===");
		msg(null, "&7 - Thanks for using &aSideBar&7 by &aEmpire92&7.");
	}

	@Override
	public void onEnable() {
		version = getDescription().getVersion();
		plugin = this;
		saveResource("english.yml", true);
		final Plugin inmePlugin = Bukkit.getServer().getPluginManager().getPlugin("IndividualMessages");
		if ((inmePlugin != null)) {
			if (inmePlugin.isEnabled()) {
				msg(null, "&7[Info] Plugin '&aIndividualMessages&7' detected. Hooking into it now.");
				individualmessages = inmePlugin;
			}
		}
		boolean isupdate = false;
		try {
			if (getConfig().getString("version").equals(version) == false) {
				msg(null, "&8===&7[&9SideBar&7]&8===");
				msg(null, "Thanks for updating SideBar");
				isupdate = true;
			}
		} catch (final Exception e) {
			isupdate = true;
		}
		getConfig().options().copyDefaults(true);
		final Map<String, Object> options = new HashMap<String, Object>();
		getConfig().set("version", version);
		options.put("language", "english");
		options.put("compatibility-mode", true);
		options.put("scripting.directory", "scripts");
		options.put("scripting.sidebar-directory", "scripts");
		options.put("scripting.debug-level", 0);
		options.put("sidebar.autoupdate.enabled", true);
		options.put("sidebar.autoupdate.interval-ticks", 20);
		options.put("sidebar.autoupdate.async", "Set to 'true' to have a lower impact on the server - may cause instability");
		final List<String> whitelist = Arrays.asList("grounded", "location", "age", "localtime", "localtime12", "display", "uses", "money", "prefix", "suffix", "group", "x", "y", "z", "lvl", "exhaustion", "health", "exp", "hunger", "air", "maxhealth", "maxair", "gamemode", "direction", "biome", "itemname", "itemid", "itemamount", "durability", "dead", "sleeping", "whitelisted", "operator", "sneaking", "itempickup", "flying", "blocking", "age", "bed", "compass", "spawn", "worldticks", "time", "date", "time12", "epoch", "epochmilli", "epochnano", "online", "worlds", "banlist", "baniplist", "operators", "whitelist", "randchoice", "rand", "elevated", "matchgroup", "matchplayer", "hasperm", "js", "gvar", "config", "passenger", "lastplayed", "gprefix", "gsuffix");
		options.put("sidebar.autoupdate.whitelist", whitelist);
		for(World world : getServer().getWorlds()) {
        	options.put("multiworld."+world.getName()+".sidebar","default");
        }
		for (final Entry<String, Object> node : options.entrySet()) {
			if (!getConfig().contains(node.getKey())) {
				getConfig().set(node.getKey(), node.getValue());
			}
		}
		try {
			final Set<String> vars = getConfig().getConfigurationSection("scripting.variables").getKeys(false);
			for (final String current : vars) {
				globals.put("{" + current + "}", getConfig().getString("scripting.variables." + current));
			}
		} catch (final Exception e) {
		}
		saveConfig();
		saveDefaultConfig();
		if (isupdate && getConfig().getString("scripting.directory").equalsIgnoreCase("scripts")) {
			final File f8 = new File(getDataFolder() + File.separator + "scripts" + File.separator + "example.yml");
			if (f8.exists() != true) {
				saveResource("scripts" + File.separator + "example.yml", false);
			}
			final File f9 = new File(getDataFolder() + File.separator + "scripts" + File.separator + "test.js");
			if (f9.exists() != true) {
				saveResource("scripts" + File.separator + "test.js", false);
			}
			final File f10 = new File(getDataFolder() + File.separator + "scoreboards" + File.separator + "default.yml");
			if (f10.exists() != true) {
				saveResource("scoreboards" + File.separator + "default.yml", false);
			}
		}
		new DefaultPlaceholders(this);
		
		final File f0 = new File(getDataFolder() + File.separator + "scoreboards");
		final File[] mysb = f0.listFiles();
		for (final File element : mysb) {
			if (element.isFile()) {
				if (element.getName().contains(".yml")) {
					try {
						final FileConfiguration yml = YamlConfiguration.loadConfiguration(element);
						String name = element.getName().substring(0, element.getName().length() - 4);
						String title;
						String permission;
						String description;
						Map<String, String> scores = new HashMap<String, String>();
						if (yml.contains("permission" )) {
							 permission = yml.getString("permission");
						}
						else {
							permission = "sidebar.use";
						}
						if (yml.contains("description" )) {
							description = yml.getString("description");
						}
						else {
							description = "No description available.";
						}
						if (yml.contains("title" )) {
							 title = yml.getString("title");
						}
						else {
							title = name;
						}
						
						List<String> keys = yml.getStringList("keys");
						List<String> values = yml.getStringList("values");
						for (int i = 0;i<keys.size();i++) {
							scores.put(keys.get(i), values.get(i));
						}
						SimpleScoreboard scoreboard = new SimpleScoreboard(title, scores, permission, description);
						simpleScoreboards.put(name, scoreboard);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
		final File f1 = new File(getDataFolder() + File.separator + getConfig().getString("scripting.directory"));
		final File[] myph = f1.listFiles();
		for (final File element : myph) {
			if (element.isFile()) {
				if (element.getName().contains(".yml")) {
					try {
						final FileConfiguration yml = YamlConfiguration.loadConfiguration(element);
						final String name = element.getName().substring(0, element.getName().length() - 4);
						final String lines = StringUtils.join(yml.getStringList("script"), ";");
						final String description;
						if (yml.contains("description")) {
							description = yml.getString("description");
						} else {
							description = "There is currently no description";
						}
						final boolean myelevation;
						final boolean asconsole;
						if (yml.contains("elevation")) {
							if (yml.getString("elevation").equalsIgnoreCase("operator")) {
								myelevation = true;
								asconsole = false;
							} else if (yml.getString("elevation").equalsIgnoreCase("console")) {
								myelevation = true;
								asconsole = true;
							} else {
								myelevation = true;
								asconsole = false;
							}
						} else {
							asconsole = false;
							myelevation = false;
						}
						addPlaceholder(new Placeholder(name) {
							@Override
							public String getDescription() {
								return description;
							}

							@Override
							public String getValue(final Player player, final Location location, final String[] modifiers, final Boolean elevation) {
								if (asconsole) {
									setUser(null);
								}
								final String toreturn = execute(lines, myelevation, location);
								if (asconsole) {
									setUser(player);
								}
								return toreturn;
							}
						});
					} catch (final Exception e2) {
						msg(null, "&cError with file " + getDataFolder() + "/" + getConfig().getString("scripting.directory") + "/" + element.getName() + ".");
					}
				}
			}
		}

		// TODO add ScoreBoards
		// I need to decide what to do for the scoreboard layout

		Bukkit.getServer().getPluginManager().registerEvents(this, this);
		if (getConfig().getBoolean("sidebar.autoupdate.enabled")) {
			timer.schedule(mytask, 1000, 50*getConfig().getLong("sidebar.autoupdate.interval-ticks"));
		}
		final Plugin factionsPlugin = Bukkit.getServer().getPluginManager().getPlugin("Factions");
		final Plugin mcorePlugin = Bukkit.getServer().getPluginManager().getPlugin("Mcore");
		if ((factionsPlugin != null) && (mcorePlugin != null)) {
			new SBFactions(this, factionsPlugin);
			msg(null, "&a[Yay] SideBar detected Factions. Additional placeholders have been added");
		}
		final Plugin vaultPlugin = Bukkit.getServer().getPluginManager().getPlugin("Vault");
		if (vaultPlugin != null) {
			new VaultFeature(this, vaultPlugin);
			msg(null, "&a[Yay] SideBar detected Vault. Additional placeholders have been added");
		}
		final Plugin enjinPlugin = Bukkit.getServer().getPluginManager().getPlugin("EnjinMinecraftPlugin");
		if (enjinPlugin != null) {
			new EnjinFeature(this, enjinPlugin);
			msg(null, "&a[Yay] SideBar detected Enjin. Additional placeholders have been added");
		}
	}
	
	@EventHandler
	public void onMapInitialize(final MapInitializeEvent event) {
		final Map<String, Object> options = new HashMap<String, Object>();
		for(World world : getServer().getWorlds()) {
        	options.put("multiworld."+world.getName()+".sidebar","default");
        }
		
		for (final Entry<String, Object> node : options.entrySet()) {
			if (!getConfig().contains(node.getKey())) {
				getConfig().set(node.getKey(), node.getValue());
			}
		}
	}
	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		if (scoreboards.containsKey(player.getName())) {
			scoreboards.remove(player.getName());
		}
		if (removeScores.containsKey(player.getName())) {
			removeScores.remove(player.getName());
		}
	}
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerJoin(final PlayerJoinEvent event) {
		Player player = event.getPlayer();
		Scoreboard sidebar;
		final ScoreboardManager manager = Bukkit.getScoreboardManager();
		player.setScoreboard(manager.getNewScoreboard());
		if (player.getScoreboard()!=null) {
			sidebar = player.getScoreboard();
		}
		else {
			sidebar = getServer().getScoreboardManager().getNewScoreboard();
		}
		Objective obj = sidebar.getObjective("sb");
		if (obj==null) {
			obj = sidebar.registerNewObjective("sb", "dummy");
		}
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		player.setScoreboard(sidebar);
		SimpleScoreboard msb = simpleScoreboards.get(getConfig().getString("multiworld."+player.getWorld().getName()+".sidebar"));
		scoreboards.put(player.getName(), msb);
	}
	@EventHandler
	public void onPlayerTeleport(final PlayerTeleportEvent event) {
		if (event.getTo().getWorld().equals(event.getFrom().getWorld()) == false) {
			SimpleScoreboard msb = simpleScoreboards.get(getConfig().getString("multiworld."+event.getTo().getWorld().getName()+".sidebar"));
			scoreboards.put(event.getPlayer().getWorld().getName(), msb);
		}
	}
//	@Override
//	public List<String> onTabComplete(final CommandSender sender, final Command cmd, final String alias, final String[] args) {
//		final List<String> toReturn = new ArrayList<String>();
//		return toReturn;
//	}

	public synchronized Placeholder removePlaceholder(final Placeholder placeholder) {
		return placeholders.remove(placeholder.getKey());
	}

	public synchronized Placeholder removePlaceholder(final String key) {
		return placeholders.remove(key);
	}

	public synchronized void setScoreboard(final Player player, final SimpleScoreboard scoreboard) {
		scoreboards.put(player.getName(), scoreboard);
	}

	public synchronized void setScoreboard(final String playername, final SimpleScoreboard scoreboard) {
		scoreboards.put(playername, scoreboard);
	}

	public void setSender(final Player player) {
		this.currentsender = player;
	}

	public void setUser(final Player player) {
		this.currentplayer = player;
	}

	public boolean testif(String mystring, final boolean elevation, final Location interact) {
		String[] args;
		if (mystring.substring(0, 2).equalsIgnoreCase("if")) {
			mystring = mystring.substring(3, mystring.length());
		}
		if (mystring.equalsIgnoreCase("false")) {
			return false;
		} else if (mystring.equalsIgnoreCase("true")) {
			return true;
		}
		int splittype = 0;
		mystring = mystring.trim();
		if (mystring.contains("!=") == true) {
			splittype = 6;
			args = mystring.split("!=");
		} else if (mystring.contains(">=") == true) {
			splittype = 4;
			args = mystring.split(">=");
		} else if (mystring.contains("<=") == true) {
			splittype = 5;
			args = mystring.split("<=");
		} else if (mystring.contains("=~") == true) {
			splittype = 7;
			args = mystring.split("=~");
		} else if (mystring.contains("==") == true) {
			splittype = 1;
			args = mystring.split("==");
		} else if (mystring.contains("=") == true) {
			splittype = 1;
			args = mystring.split("=");
		} else if (mystring.contains(">") == true) {
			splittype = 2;
			args = mystring.split(">");
		} else if (mystring.contains("<") == true) {
			splittype = 3;
			args = mystring.split("<");
		} else if (mystring.contains("!") == true) {
			splittype = 6;
			args = mystring.split("!");
		} else {
			msg(null, "ERR(syntax): " + mystring);
			return false;
		}
		final boolean toreturn = false;
		final String left = args[0].trim();
		final String right = args[1].trim();
		Object result1 = null;
		Object result2 = null;
		boolean evaluated = false;
		try {
			result1 = engine.eval(left);
			result2 = engine.eval(right);
			evaluated = true;
			if (splittype == 1) {
				return (result1.equals(result2));
			} else if (splittype == 6) {
				return !(result1.equals(result2));
			}
			if ((result1 instanceof Double) && (result2 instanceof Double)) {
				final Double result3 = (Double) result1;
				final Double result4 = (Double) result2;
				if (splittype == 2) {
					return (result3 > result4);
				}
				if (splittype == 3) {
					return (result3 < result4);
				} else if (splittype == 4) {
					return (result3 >= result4);
				} else if (splittype == 5) {
					return (result3 <= result4);
				}
			}
			if ((result1 instanceof Integer) && (result2 instanceof Integer)) {
				final Integer result3 = (Integer) result1;
				final Integer result4 = (Integer) result2;
				if (splittype == 2) {
					return (result3 > result4);
				} else if (splittype == 3) {
					return (result3 < result4);
				} else if (splittype == 4) {
					return (result3 >= result4);
				} else if (splittype == 5) {
					return (result3 <= result4);
				}
			}
			if ((result1 instanceof Float) && (result2 instanceof Float)) {
				final Float result3 = (Float) result1;
				final Float result4 = (Float) result2;
				if (splittype == 2) {
					return (result3 > result4);
				} else if (splittype == 3) {
					return (result3 < result4);
				} else if (splittype == 4) {
					return (result3 >= result4);
				} else if (splittype == 5) {
					return (result3 <= result4);
				}
			}
			if ((result1 instanceof Long) && (result2 instanceof Long)) {
				final Long result3 = (Long) result1;
				final Long result4 = (Long) result2;
				if (splittype == 2) {
					return (result3 > result4);
				} else if (splittype == 3) {
					return (result3 < result4);
				} else if (splittype == 4) {
					return (result3 >= result4);
				} else if (splittype == 5) {
					return (result3 <= result4);
				}
			}
		} catch (final Exception e) {
			if (evaluated) {
				msg(null, "ERR(syntax): " + mystring);
			}
		}
		if (splittype == 1) {
			return (left.equals(right));
		} else if (splittype == 2) {
			return (left.compareTo(right) > 0);
		} else if (splittype == 3) {
			return (left.compareTo(right) < 0);
		} else if (splittype == 4) {
			return (left.compareTo(right) >= 0);
		} else if (splittype == 5) {
			return (left.compareTo(right) <= 0);
		} else if (splittype == 6) {
			return !(result1.equals(result2));
		} else if (splittype == 7) {
			return (left.equalsIgnoreCase(right));
		}
		msg(null, "ERR(syntax): " + mystring);
		return toreturn;
	}
}
