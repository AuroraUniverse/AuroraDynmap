package org.dynmap.towny;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.dynmap.DynmapAPI;
import org.dynmap.DynmapWebChatEvent;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;
import org.dynmap.markers.PlayerSet;


import org.dynmap.towny.events.BuildTownFlagsEvent;
import org.dynmap.towny.events.BuildTownMarkerDescriptionEvent;
import ru.etysoft.aurorauniverse.AuroraUniverse;
import ru.etysoft.aurorauniverse.data.Nations;
import ru.etysoft.aurorauniverse.data.Towns;
import ru.etysoft.aurorauniverse.exceptions.TownNotFoundedException;
import ru.etysoft.aurorauniverse.world.*;

public class DynmapTownyPlugin extends JavaPlugin {


    private static Logger log;
    private static String DEF_INFOWINDOW ;
    private static final String NATION_NONE = "_none_";
    Plugin dynmap;
    DynmapAPI api;
    MarkerAPI markerapi;

    int townblocksize;

    boolean reload = false;
    private boolean playersbytown;
    private boolean playersbynation;
    private boolean dynamicNationColorsEnabled;
    private boolean dynamicTownColorsEnabled;

    FileConfiguration cfg;
    MarkerSet set;
    long updperiod;
    boolean use3d;
    String infowindow;
    AreaStyle defstyle;
    Map<String, AreaStyle> cusstyle;
    Map<String, AreaStyle> nationstyle;
    Set<String> visible;
    Set<String> hidden;
    boolean show_shops;
    boolean show_arenas;
    boolean show_embassies;
    boolean show_wilds;
    boolean stop;
    boolean using_townychat = false;
    boolean chat_sendlogin;
    boolean chat_sendquit;
    String chatformat;

    @Override
    public void onLoad() {
        log = this.getLogger();
    }

    private static class AreaStyle {
        int strokecolor;
        double strokeopacity;
        int strokeweight;
        int fillcolor;
        double fillopacity;
        int fillcolor_shops;
        int fillcolor_embassies;
        int fillcolor_arenas;
        int fillcolor_wilds;
        String homemarker;
        String capitalmarker;
        MarkerIcon homeicon;
        MarkerIcon capitalicon;
        MarkerIcon ruinicon;
        int yc;
        boolean boost;

        AreaStyle(FileConfiguration cfg, String path, MarkerAPI markerapi) {
            String sc = cfg.getString(path + ".strokeColor", null);
            strokeopacity = cfg.getDouble(path + ".strokeOpacity", -1);
            strokeweight = cfg.getInt(path + ".strokeWeight", -1);
            String fc = cfg.getString(path + ".fillColor", null);
            String fcs = cfg.getString(path + ".fillColorShops", null);
            String fca = cfg.getString(path + ".fillColorArenas", null);
            String fce = cfg.getString(path + ".fillColorEmbassies", null);
            String fcw = cfg.getString(path + ".fillColorWilds", null);
            yc = cfg.getInt(path + ".y", -1);
            boost = cfg.getBoolean(path + ".boost", false);

            strokecolor = -1;
            fillcolor = -1;
            fillcolor_shops = -1;
            fillcolor_arenas = -1;
            fillcolor_embassies = -1;
            fillcolor_wilds = -1;
            try {
                if (sc != null)
                    strokecolor = Integer.parseInt(sc.substring(1), 16);
                if (fc != null)
                    fillcolor = Integer.parseInt(fc.substring(1), 16);
                if (fcs != null)
                    fillcolor_shops = Integer.parseInt(fcs.substring(1), 16);
                if (fca != null)
                    fillcolor_arenas = Integer.parseInt(fca.substring(1), 16);
                if (fce != null)
                    fillcolor_embassies = Integer.parseInt(fce.substring(1), 16);
                if (fcw != null)
                    fillcolor_wilds = Integer.parseInt(fcw.substring(1), 16);
            } catch (NumberFormatException nfx) {
            }

            fillopacity = cfg.getDouble(path + ".fillOpacity", -1);
            homemarker = cfg.getString(path + ".homeicon", null);
            if (homemarker != null) {
                homeicon = markerapi.getMarkerIcon(homemarker);
                if (homeicon == null) {
                    severe("Invalid homeicon: " + homemarker);
                    homeicon = markerapi.getMarkerIcon("blueicon");
                }
            }
            capitalmarker = cfg.getString(path + ".capitalicon", null);
            if (capitalmarker != null) {
                capitalicon = markerapi.getMarkerIcon(capitalmarker);
                if (capitalicon == null) {
                    severe("Invalid capitalicon: " + capitalmarker);
                    capitalicon = markerapi.getMarkerIcon("king");
                }
            }
            ruinicon = markerapi.getMarkerIcon("warning");
        }

        public int getStrokeColor(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.strokecolor >= 0))
                return cust.strokecolor;
            else if ((nat != null) && (nat.strokecolor >= 0))
                return nat.strokecolor;
            else if (strokecolor >= 0)
                return strokecolor;
            else
                return 0xFF0000;
        }

        public double getStrokeOpacity(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.strokeopacity >= 0))
                return cust.strokeopacity;
            else if ((nat != null) && (nat.strokeopacity >= 0))
                return nat.strokeopacity;
            else if (strokeopacity >= 0)
                return strokeopacity;
            else
                return 0.8;
        }

        public int getStrokeWeight(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.strokeweight >= 0))
                return cust.strokeweight;
            else if ((nat != null) && (nat.strokeweight >= 0))
                return nat.strokeweight;
            else if (strokeweight >= 0)
                return strokeweight;
            else
                return 3;
        }

        public int getFillColor(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.fillcolor >= 0))
                return cust.fillcolor;
            else if ((nat != null) && (nat.fillcolor >= 0))
                return nat.fillcolor;
            else if (fillcolor >= 0)
                return fillcolor;
            else
                return 0xFF0000;
        }

        public double getFillOpacity(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.fillopacity >= 0))
                return cust.fillopacity;
            else if ((nat != null) && (nat.fillopacity >= 0))
                return nat.fillopacity;
            else if (fillopacity >= 0)
                return fillopacity;
            else
                return 0.35;
        }

        public MarkerIcon getHomeMarker(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.homeicon != null))
                return cust.homeicon;
            else if ((nat != null) && (nat.homeicon != null))
                return nat.homeicon;
            else
                return homeicon;
        }

        public MarkerIcon getCapitalMarker(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.capitalicon != null))
                return cust.capitalicon;
            else if ((nat != null) && (nat.capitalicon != null))
                return nat.capitalicon;
            else if (capitalicon != null)
                return capitalicon;
            else
                return getHomeMarker(cust, nat);
        }

        public int getY(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && (cust.yc >= 0))
                return cust.yc;
            else if ((nat != null) && (nat.yc >= 0))
                return nat.yc;
            else if (yc >= 0)
                return yc;
            else
                return 64;
        }

        public boolean getBoost(AreaStyle cust, AreaStyle nat) {
            if ((cust != null) && cust.boost)
                return cust.boost;
            else if ((nat != null) && nat.boost)
                return nat.boost;
            else
                return boost;
        }
    }

    public static void info(String msg) {
        log.log(Level.INFO, msg);
    }

    public static void severe(String msg) {
        log.log(Level.SEVERE, msg);
    }

    private class TownyUpdate implements Runnable {
        public void run() {
            if (!stop) {
                if (AuroraUniverse.getInstance() == null) {
                    stop = true;
                } else {
                    updateTowns();
                    updateTownPlayerSets();
                    updateNationPlayerSets();
                }
            }
        }
    }

    /*
    private class TownyUpdateReq implements Runnable {
        public void run() {
            if(!stop) {
                pending_upd_req = null;
                
                updateTowns();
            }
        }
    }
    private TownyUpdateReq pending_upd_req = null;
    private void requestUpdateTownMap(Town t) {
        info("requestUpdateTownMap("+ t.getTag() + ")");
        if(pending_upd_req == null) {
            pending_upd_req = new TownyUpdateReq();
            getServer().getScheduler().scheduleSyncDelayedTask(this, pending_upd_req, 20);
        }
    }*/

    private void updateTown(Town town) {
        if (!playersbytown) return;
        Set<String> plids = new HashSet<String>();
        List<Resident> res = town.getResidents();
        for (Resident r : res) {
            plids.add(r.getName());
        }
        String setid = "towny.town." + town.getName();
        PlayerSet set = markerapi.getPlayerSet(setid);  /* See if set exists */
        if (set == null) {
            set = markerapi.createPlayerSet(setid, true, plids, false);
            info("Added player visibility set '" + setid + "' for town " + town.getName());
        } else {
            set.setPlayers(plids);
        }
    }

    private void updateTownPlayerSets() {
        if (!playersbytown) return;
        for (String name : AuroraUniverse.getTownList().keySet()) {
            Town t = null;
            try {
                t = Towns.getTown(name);
                updateTown(t);
            } catch (TownNotFoundedException e) {
                e.printStackTrace();
            }

        }
    }

    private void updateNation(Nation nat) {
        if (!playersbynation) return;
        Set<String> plids = new HashSet<String>();
        // TODO: resident in nation
//        List<Resident> res = nat;
//        for(Resident r : res) {
//            plids.add(r.getName());
//        }
        String setid = "towny.nation." + nat.getName();
        PlayerSet set = markerapi.getPlayerSet(setid);  /* See if set exists */
        if (set == null) {
            set = markerapi.createPlayerSet(setid, true, plids, false);
            info("Added player visibility set '" + setid + "' for nation " + nat.getName());
        } else {
            set.setPlayers(plids);
        }
    }

    private void updateNationPlayerSets() {
        if (!playersbynation) return;
        for (String name : AuroraUniverse.nationList.keySet()) {
            updateNation(Nations.getNation(name));
        }
    }

    
    /* Cannot do this until towny add/remove player events are fixed
    private class PlayerUpdate implements Runnable {
        public void run() {
            pending_upd = null;
            if(stop) return;
                
            for(Town t : town_to_upd) {
                updateTown(t);
            }
            for(Nation n : nation_to_upd) {
                updateNation(n);
            }
            town_to_upd.clear();
            nation_to_upd.clear();
        }
    }
    
    private HashSet<Town> town_to_upd = new HashSet<Town>();
    private HashSet<Nation> nation_to_upd = new HashSet<Nation>();
    private PlayerUpdate pending_upd;
    
    private void requestUpdateTownPlayers(Town t) {
        if(playersbytown)
            town_to_upd.add(t);
        try {
            if(playersbynation && t.hasNation())
                nation_to_upd.add(t.getNation());
        } catch (NotRegisteredException nrx) {
        }
        if(pending_upd == null) {
            pending_upd = new PlayerUpdate();
            getServer().getScheduler().scheduleSyncDelayedTask(this, pending_upd, 20);
        }
    }
    */

    private Map<String, AreaMarker> resareas = new HashMap<String, AreaMarker>();
    private Map<String, Marker> resmark = new HashMap<String, Marker>();

    private String formatInfoWindow(Town town) {
        String v = "<div class=\"regioninfo\">" + infowindow + "</div>";

            v = v.replace("%regionname%", town.getName());
        v = v.replace("%playerowners%", town.getMayor().getName());
        String res = "";
        for (Resident r : town.getResidents()) {
            if (res.length() > 0) res += ", ";
            res += r.getName();
        }
        v = v.replace("%playermembers%", res);
        String mgrs = "";
        // TODO: assistants
//        for(Resident r : town.getRank("assistant")) {
//            if(mgrs.length()>0) mgrs += ", ";
//            mgrs += r.getName();
//        }
        v = v.replace("%playermanagers%", res);

        String dispNames = "";
        for (Resident r : town.getResidents()) {
            Player p = Bukkit.getPlayer(r.getName());
            if (dispNames.length() > 0) mgrs += ", ";

            if (p == null) {
                dispNames += r.getName();
                continue;
            }

            dispNames += p.getDisplayName();
        }

        v = v.replace("%residentdisplaynames%", dispNames);

        v = v.replace("%residentcount%", town.getResidents().size() + "");

        //TODO: founded
        v = v.replace("%founded%", "Нет");
        //TODO: board
        v = v.replace("%board%", "Нет");

        v = v.replace("%tax%", town.getResTax() + "");
        String nation = "";
        if (town.hasNation())
            nation = town.getNationName();

        v = v.replace("%nation%", nation);

        String natStatus = "";
        if(town.getNation() != null) {
            if (town.getNation().getCapital() == town) {
                natStatus = "Столица " + nation;
            } else if (town.hasNation()) {
                natStatus = "Часть " + nation;
            }
        }
        v = v.replace("%nationstatus%", natStatus);


        v = v.replace("%upkeep%", String.valueOf(town.getTownTax()));

        v = v.replace("%public%", "false");
        v = v.replace("%peaceful%", "false");


        /* Build flags */
        List<String> flags = new ArrayList<>();

        flags.add("PvP: " + boolToString(town.isTownPvp()));
        flags.add(getConfig().getString("strings.monsters") + boolToString(town.isMobs()));
        flags.add(getConfig().getString("strings.exp") + boolToString(town.isExplosionEnabled()));
        flags.add(getConfig().getString("strings.fire") + boolToString(town.isFire()));
        flags.add(getConfig().getString("strings.nation") + nation);


        BuildTownFlagsEvent buildTownFlagsEvent = new BuildTownFlagsEvent(town, flags);
        Bukkit.getPluginManager().callEvent(buildTownFlagsEvent);

        v = v.replace("%flags%", String.join("<br/>", buildTownFlagsEvent.getFlags()));

        return v;
    }

    private boolean isVisible(String id, String worldname) {
        if ((visible != null) && (visible.size() > 0)) {
            if ((visible.contains(id) == false) && (visible.contains("world:" + worldname) == false)) {
                return false;
            }
        }
        if ((hidden != null) && (hidden.size() > 0)) {
            if (hidden.contains(id) || hidden.contains("world:" + worldname))
                return false;
        }
        return true;
    }

    private String boolToString(boolean b)
    {
        if(b) return getConfig().getString("strings.on-st");
        return getConfig().getString("strings.off-st");
    }

    private void addStyle(Town town, String resid, String natid, AreaMarker m) {
        AreaStyle as = cusstyle.get(resid);    /* Look up custom style for town, if any */
        AreaStyle ns = nationstyle.get(natid);    /* Look up nation style, if any */

            m.setLineStyle(defstyle.getStrokeWeight(as, ns), defstyle.getStrokeOpacity(as, ns), defstyle.getStrokeColor(as, ns));

        m.setFillStyle(defstyle.getFillOpacity(as, ns), defstyle.getFillColor(as, ns));
        double y = defstyle.getY(as, ns);
        m.setRangeY(y, y);
        m.setBoostFlag(defstyle.getBoost(as, ns));

        //Read dynamic colors from town/nation objects
        if (dynamicTownColorsEnabled || dynamicNationColorsEnabled) {
            try {
                String colorHexCode;
                Integer townFillColorInteger = null;
                Integer townBorderColorInteger = null;

                //CALCULATE FILL COLOUR
                if (dynamicTownColorsEnabled) {
                    //Here we know the server is using town colors. This takes top priority for fill
//                    colorHexCode = null;
//                    if(!colorHexCode.isEmpty()) {
//                        //If town has a color, use it
//                        townFillColorInteger = Integer.parseInt(colorHexCode, 16);
//                    }
                } else {
                    //Here we know the server is using nation colors
//                    colorHexCode = town.getNationMapColorHexCode();
//                    if(colorHexCode != null && !colorHexCode.isEmpty()) {
//                        //If nation has a color, use it
//                        townFillColorInteger = Integer.parseInt(colorHexCode, 16);
//                    }
                }

                //CALCULATE BORDER COLOR
                if (dynamicNationColorsEnabled) {
                    //Here we know the server is using nation colors. This takes top priority for border
//                    colorHexCode = town.getNationMapColorHexCode();
//                    if(colorHexCode != null && !colorHexCode.isEmpty()) {
//                        //If nation has a color, use it
//                        townBorderColorInteger = Integer.parseInt(colorHexCode, 16);
//                    }
                } else {
                    //Here we know the server is using town colors
//                    colorHexCode = town.getMapColorHexCode();
//                    if(!colorHexCode.isEmpty()) {
//                        //If town has a color, use it
//                        townBorderColorInteger = Integer.parseInt(colorHexCode, 16);
//                    }
                }

                //SET FILL COLOR
                if (townFillColorInteger != null) {
                    //Set fill style
                    double fillOpacity = m.getFillOpacity();
                    m.setFillStyle(fillOpacity, townFillColorInteger);
                }

                //SET BORDER COLOR
                if (townBorderColorInteger != null) {
                    //Set stroke style
                    double strokeOpacity = m.getLineOpacity();
                    int strokeWeight = m.getLineWeight();
                    m.setLineStyle(strokeWeight, strokeOpacity, townBorderColorInteger);
                }

            } catch (Exception ex) {
            }
        }
    }

    private MarkerIcon getMarkerIcon(Town town) {


        String id = town.getName();
        AreaStyle as = cusstyle.get(id);
        String natid = NATION_NONE;
        try {
            if (town.getNation() != null)
                natid = town.getNation().getName();
        } catch (Exception ex) {
        }
        AreaStyle ns = nationstyle.get(natid);

        if (town.getNation() != null && town.getNation().getCapital() == town)
            return defstyle.getCapitalMarker(as, ns);
        else
            return defstyle.getHomeMarker(as, ns);
    }

    enum direction {XPLUS, ZPLUS, XMINUS, ZMINUS}

    ;

    /**
     * Find all contiguous blocks, set in target and clear in source
     */
    private int floodFillTarget(TileFlags src, TileFlags dest, int x, int y) {
        int cnt = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<int[]>();
        stack.push(new int[]{x, y});

        while (stack.isEmpty() == false) {
            int[] nxt = stack.pop();
            x = nxt[0];
            y = nxt[1];
            if (src.getFlag(x, y)) { /* Set in src */
                src.setFlag(x, y, false);   /* Clear source */
                dest.setFlag(x, y, true);   /* Set in destination */
                cnt++;
                if (src.getFlag(x + 1, y))
                    stack.push(new int[]{x + 1, y});
                if (src.getFlag(x - 1, y))
                    stack.push(new int[]{x - 1, y});
                if (src.getFlag(x, y + 1))
                    stack.push(new int[]{x, y + 1});
                if (src.getFlag(x, y - 1))
                    stack.push(new int[]{x, y - 1});
            }
        }
        return cnt;
    }

    /* Handle specific town */
    private void handleTown(Town town, Map<String, AreaMarker> newmap, Map<String, Marker> newmark) {
        String name = town.getName();
        double[] x = null;
        double[] z = null;
        int poly_index = 0; /* Index of polygon for given town */

        /* Handle areas */
        Map<ChunkPair, Region> blocks = town.getTownChunks();
        if (blocks.isEmpty())
            return;
        /* Build popup */
        BuildTownMarkerDescriptionEvent event = new BuildTownMarkerDescriptionEvent(town, formatInfoWindow(town));
        Bukkit.getPluginManager().callEvent(event);
        String desc = event.getDescription();

        HashMap<String, TileFlags> blkmaps = new HashMap<String, TileFlags>();
        LinkedList<ChunkPair> nodevals = new LinkedList<ChunkPair>();
        TileFlags curblks = null;

        World curworld = null;

        boolean vis = false;
        /* Loop through blocks: set flags on blockmaps for worlds */
        for (ChunkPair chunks : blocks.keySet()) {
            /* If we're scanning for specific type, and this isn't it, skip */
            Region region = blocks.get(chunks);

            if (chunks.getWorld() != curworld) { /* Not same world */
                String wname = chunks.getWorld().getName();
                vis = isVisible(name, wname);  /* See if visible */
                if (vis) {  /* Only accumulate for visible areas */
                    curblks = blkmaps.get(wname);  /* Find existing */
                    if (curblks == null) {
                        curblks = new TileFlags();
                        blkmaps.put(wname, curblks);   /* Add fresh one */
                    }
                }
                curworld = chunks.getWorld();
                vis = false;
            }
            else
            {
                vis = true;
            }
            if (vis) {
                curblks.setFlag(chunks.getX(), chunks.getZ(), true); /* Set flag for block */
                nodevals.addLast(chunks);
            }
        }
        /* Loop through until we don't find more areas */
        while (nodevals != null) {
            LinkedList<ChunkPair> ournodes = null;
            LinkedList<ChunkPair> newlist = null;
            TileFlags ourblks = null;
            int minx = Integer.MAX_VALUE;
            int minz = Integer.MAX_VALUE;
            for (ChunkPair node : nodevals) {
                int nodex = node.getX();
                int nodez = node.getZ();
                if (ourblks == null) {   /* If not started, switch to world for this block first */
                    if (node.getWorld() != curworld) {
                        curworld = node.getWorld();
                        curblks = blkmaps.get(curworld.getName());
                    }
                }
                /* If we need to start shape, and this block is not part of one yet */
                if ((ourblks == null) && curblks.getFlag(nodex, nodez)) {
                    ourblks = new TileFlags();  /* Create map for shape */
                    ournodes = new LinkedList<ChunkPair>();
                    floodFillTarget(curblks, ourblks, nodex, nodez);   /* Copy shape */
                    ournodes.add(node); /* Add it to our node list */
                    minx = nodex;
                    minz = nodez;
                }
                /* If shape found, and we're in it, add to our node list */
                else if ((ourblks != null) && (node.getWorld() == curworld) &&
                        (ourblks.getFlag(nodex, nodez))) {
                    ournodes.add(node);
                    if (nodex < minx) {
                        minx = nodex;
                        minz = nodez;
                    } else if ((nodex == minx) && (nodez < minz)) {
                        minz = nodez;
                    }
                } else {  /* Else, keep it in the list for the next polygon */
                    if (newlist == null) newlist = new LinkedList<ChunkPair>();
                    newlist.add(node);
                }
            }
            nodevals = newlist; /* Replace list (null if no more to process) */
            if (ourblks != null) {
                /* Trace outline of blocks - start from minx, minz going to x+ */
                int init_x = minx;
                int init_z = minz;
                int cur_x = minx;
                int cur_z = minz;
                direction dir = direction.XPLUS;
                ArrayList<int[]> linelist = new ArrayList<int[]>();
                linelist.add(new int[]{init_x, init_z}); // Add start point
                while ((cur_x != init_x) || (cur_z != init_z) || (dir != direction.ZMINUS)) {
                    switch (dir) {
                        case XPLUS: /* Segment in X+ direction */
                            if (!ourblks.getFlag(cur_x + 1, cur_z)) { /* Right turn? */
                                linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                                dir = direction.ZPLUS;  /* Change direction */
                            } else if (!ourblks.getFlag(cur_x + 1, cur_z - 1)) {  /* Straight? */
                                cur_x++;
                            } else {  /* Left turn */
                                linelist.add(new int[]{cur_x + 1, cur_z}); /* Finish line */
                                dir = direction.ZMINUS;
                                cur_x++;
                                cur_z--;
                            }
                            break;
                        case ZPLUS: /* Segment in Z+ direction */
                            if (!ourblks.getFlag(cur_x, cur_z + 1)) { /* Right turn? */
                                linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                                dir = direction.XMINUS;  /* Change direction */
                            } else if (!ourblks.getFlag(cur_x + 1, cur_z + 1)) {  /* Straight? */
                                cur_z++;
                            } else {  /* Left turn */
                                linelist.add(new int[]{cur_x + 1, cur_z + 1}); /* Finish line */
                                dir = direction.XPLUS;
                                cur_x++;
                                cur_z++;
                            }
                            break;
                        case XMINUS: /* Segment in X- direction */
                            if (!ourblks.getFlag(cur_x - 1, cur_z)) { /* Right turn? */
                                linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                                dir = direction.ZMINUS;  /* Change direction */
                            } else if (!ourblks.getFlag(cur_x - 1, cur_z + 1)) {  /* Straight? */
                                cur_x--;
                            } else {  /* Left turn */
                                linelist.add(new int[]{cur_x, cur_z + 1}); /* Finish line */
                                dir = direction.ZPLUS;
                                cur_x--;
                                cur_z++;
                            }
                            break;
                        case ZMINUS: /* Segment in Z- direction */
                            if (!ourblks.getFlag(cur_x, cur_z - 1)) { /* Right turn? */
                                linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                                dir = direction.XPLUS;  /* Change direction */
                            } else if (!ourblks.getFlag(cur_x - 1, cur_z - 1)) {  /* Straight? */
                                cur_z--;
                            } else {  /* Left turn */
                                linelist.add(new int[]{cur_x, cur_z}); /* Finish line */
                                dir = direction.XMINUS;
                                cur_x--;
                                cur_z--;
                            }
                            break;
                    }
                }
                /* Build information for specific area */
                String polyid = town.getName() + "__" + poly_index;

                int sz = linelist.size();
                x = new double[sz];
                z = new double[sz];
                for (int i = 0; i < sz; i++) {
                    int[] line = linelist.get(i);
                    x[i] = (double) line[0] * (double) townblocksize;
                    z[i] = (double) line[1] * (double) townblocksize;
                }
                /* Find existing one */
                AreaMarker m = resareas.remove(polyid); /* Existing area? */
                if (m == null) {
                    m = set.createAreaMarker(polyid, name, false, curworld.getName(), x, z, false);
                    if (m == null) {
                        info("error adding area marker " + polyid + " " + curworld.getName());
                        return;
                    }
                } else {
                    m.setCornerLocations(x, z); /* Replace corner locations */
                    m.setLabel(name);   /* Update label */
                }
                /* Set popup */
                m.setDescription(desc);
                /* Set line and fill properties */
                String nation = NATION_NONE;
                try {
                    if (town.getNation() != null)
                        nation = town.getNation().getName();
                } catch (Exception ex) {
                }
                addStyle(town, town.getName(), nation, m);

                /* Add to map */
                newmap.put(polyid, m);
                poly_index++;
            }
        }

            /* Now, add marker for home block */
            ChunkPair blk = null;
            try {
                blk = town.getMainChunk();
            } catch (Exception ex) {
                severe("getHomeBlock exception " + ex);
            }
            if ((blk != null) && isVisible(name, blk.getWorld().getName())) {
                String markid = town.getName() + "__home";
                MarkerIcon ico = getMarkerIcon(town);
                if (ico != null) {
                    Marker home = resmark.remove(markid);
                    double xx = townblocksize * blk.getX() + (townblocksize / 2);
                    double zz = townblocksize * blk.getZ() + (townblocksize / 2);
                    if (home == null) {
                        home = set.createMarker(markid, name, blk.getWorld().getName(),
                                xx, 64, zz, ico, false);
                        if (home == null)
                            return;
                    } else {
                        home.setLocation(blk.getWorld().getName(), xx, 64, zz);
                        home.setLabel(name);   /* Update label */
                        home.setMarkerIcon(ico);
                    }
                    /* Set popup */
                    home.setDescription(desc);

                    newmark.put(markid, home);
                }
            }

    }

    /* Update Towny information */
    private void updateTowns() {
        Map<String, AreaMarker> newmap = new HashMap<String, AreaMarker>(); /* Build new map */
        Map<String, Marker> newmark = new HashMap<String, Marker>(); /* Build new map */

        /* Loop through towns */
        Collection<Town> towns = AuroraUniverse.townList.values();
        for (Town t : towns) {

            handleTown(t, newmap, newmark);

        }
        /* Now, review old map - anything left is gone */
        for (AreaMarker oldm : resareas.values()) {
            oldm.deleteMarker();
        }
        for (Marker oldm : resmark.values()) {
            oldm.deleteMarker();
        }
        /* And replace with new map */
        resareas = newmap;
        resmark = newmark;

    }

    private class OurServerListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onWebchatEvent(DynmapWebChatEvent event) {
            if (using_townychat && !chatformat.isEmpty()) {
                if (!event.isCancelled() && !event.isProcessed()) {
                    event.setProcessed();
                    String msg = chatformat.replace("&color;", "\u00A7").replace("%playername%", event.getName()).replace("%message%", event.getMessage());
                    getServer().broadcastMessage(msg);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerLogin(PlayerLoginEvent event) {
            if (chat_sendlogin && using_townychat && event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
                Player player = event.getPlayer();
                api.postPlayerJoinQuitToWeb(player, true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            if (chat_sendquit && using_townychat) {
                Player player = event.getPlayer();
                api.postPlayerJoinQuitToWeb(player, false);
            }
        }

        @EventHandler(priority = EventPriority.NORMAL)
        private void onDynMapReload(PluginEnableEvent event) {
            if (event.getPlugin().getName().equals("dynmap")) {
                PluginManager pluginManager = getServer().getPluginManager();
                if (pluginManager.isPluginEnabled("Dynmap-Towny")) {
                    pluginManager.disablePlugin(DynmapTownyPlugin.this);
                    pluginManager.enablePlugin(DynmapTownyPlugin.this);
                }
            }

        }

        /*
        @EventHandler(priority=EventPriority.MONITOR)
        public void onChangePlot(PlayerChangePlotEvent event) {
            WorldCoord fromblk = event.getFrom();
            WorldCoord toblk = event.getFrom();
            try {
                TownBlock tb = fromblk.getTownBlock();
                if(tb != null) {
                    Town t = tb.getTown();
                    if(t != null) {
                        reque)stUpdateTownMap(t);
                    }
                }
            } catch (NotRegisteredException nrx) {
            }
            try {
                TownBlock tb = toblk.getTownBlock();
                if(tb != null) {
                    Town t = tb.getTown();
                    if(t != null) {
                        requestUpdateTownMap(t);
                    }
                }
            } catch (NotRegisteredException nrx) {
            }
        }
        */
    }

    public void onEnable() {
        info("initializing");
        DEF_INFOWINDOW = "<div class=\"infowindow\"><span style=\"font-size:120%;\">%regionname% (%nation%)</span><br /> " +
              getConfig().getString("strings.mayor")  + " <span style=\"font-weight:bold;\">%playerowners%</span><br /> " +
                getConfig().getString("strings.residents") +" <span style=\"font-weight:bold;\">%playermanagers%</span><br/>"+
                getConfig().getString("strings.flags") +"<br /><span style=\"font-weight:bold;\">%flags%</span></div>";
        PluginManager pm = getServer().getPluginManager();
        /* Get dynmap */
        dynmap = pm.getPlugin("dynmap");
        if (dynmap == null) {
            severe("Cannot find dynmap!");
            return;
        }
        api = (DynmapAPI) dynmap; /* Get API */


        getLogger().info("Aurora version " + AuroraUniverse.getInstance().getDescription().getVersion() + " found.");


        getServer().getPluginManager().registerEvents(new OurServerListener(), this);
        /* If both enabled, activate */
        if (dynmap.isEnabled() && AuroraUniverse.getInstance().isEnabled()) {
            activate();
            prepForChat();
        }
    }

    private void prepForChat() {
        /* If townychat is active, and we've found dynmap API */

    }

    private void activate() {
        markerapi = api.getMarkerAPI();
        if (markerapi == null) {
            severe("Error loading dynmap marker API!");
            return;
        }
        /* Connect to towny API */

        townblocksize = 16;

        /* Load configuration */
        if (reload) {
            reloadConfig();
            if (set != null) {
                set.deleteMarkerSet();
                set = null;
            }
        } else {
            reload = true;
        }
        FileConfiguration cfg = getConfig();
        cfg.options().copyDefaults(true);   /* Load defaults, if needed */
        this.saveConfig();  /* Save updates, if needed */

        /* Now, add marker set for mobs (make it transient) */
        set = markerapi.getMarkerSet("towny.markerset");
        if (set == null)
            set = markerapi.createMarkerSet("towny.markerset", cfg.getString("layer.name", "AuroraUniverse"), null, false);
        else
            set.setMarkerSetLabel(cfg.getString("layer.name", "AuroraUniverse"));
        if (set == null) {
            severe("Error creating marker set");
            return;
        }
        int minzoom = cfg.getInt("layer.minzoom", 0);
        if (minzoom > 0)
            set.setMinZoom(minzoom);
        set.setLayerPriority(cfg.getInt("layer.layerprio", 10));
        set.setHideByDefault(cfg.getBoolean("layer.hidebydefault", false));
        use3d = cfg.getBoolean("use3dregions", false);
        infowindow = cfg.getString("infowindow", DEF_INFOWINDOW);
        /* See if we need to show commercial areas */
        show_shops = cfg.getBoolean("layer.showShops", false);
        show_arenas = cfg.getBoolean("layer.showArenas", false);
        show_embassies = cfg.getBoolean("layer.showEmbassies", false);
        show_wilds = cfg.getBoolean("layer.showWilds", false);

        /* Get style information */
        defstyle = new AreaStyle(cfg, "regionstyle", markerapi);
        cusstyle = new HashMap<String, AreaStyle>();
        nationstyle = new HashMap<String, AreaStyle>();
        ConfigurationSection sect = cfg.getConfigurationSection("custstyle");
        if (sect != null) {
            Set<String> ids = sect.getKeys(false);

            for (String id : ids) {
                cusstyle.put(id, new AreaStyle(cfg, "custstyle." + id, markerapi));
            }
        }
        sect = cfg.getConfigurationSection("nationstyle");
        if (sect != null) {
            Set<String> ids = sect.getKeys(false);

            for (String id : ids) {
                nationstyle.put(id, new AreaStyle(cfg, "nationstyle." + id, markerapi));
            }
        }
        List<String> vis = cfg.getStringList("visibleregions");
        if (vis != null) {
            visible = new HashSet<String>(vis);
        }
        List<String> hid = cfg.getStringList("hiddenregions");
        if (hid != null) {
            hidden = new HashSet<String>(hid);
        }

        chat_sendlogin = cfg.getBoolean("chat.sendlogin", true);
        chat_sendquit = cfg.getBoolean("chat.sendquit", true);
        chatformat = cfg.getString("chat.format", "&color;2[WEB] %playername%: &color;f%message%");

        /* Check if player sets enabled */
        playersbytown = cfg.getBoolean("visibility-by-town", false);
        if (playersbytown) {
            try {
                if (!api.testIfPlayerInfoProtected()) {
                    playersbytown = false;
                    info("Dynmap does not have player-info-protected enabled - visibility-by-town will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersbytown = false;
                info("Dynmap does not support function needed for 'visibility-by-town' - need to upgrade to 0.60 or later");
            }
        }
        playersbynation = cfg.getBoolean("visibility-by-nation", false);
        if (playersbynation) {
            try {
                if (!api.testIfPlayerInfoProtected()) {
                    playersbynation = false;
                    info("Dynmap does not have player-info-protected enabled - visibility-by-nation will have no effect");
                }
            } catch (NoSuchMethodError x) {
                playersbynation = false;
                info("Dynmap does not support function needed for 'visibility-by-nation' - need to upgrade to 0.60 or later");
            }
        }

        dynamicNationColorsEnabled = cfg.getBoolean("dynamic-nation-colors", true);
        dynamicTownColorsEnabled = cfg.getBoolean("dynamic-town-colors", true);

        /* Set up update job - based on periond */
        int per = cfg.getInt("update.period", 300);
        if (per < 15) per = 15;
        updperiod = (per * 20);
        stop = false;

        getServer().getScheduler().runTaskTimerAsynchronously(this, new TownyUpdate(), 40, per);

        info("version " + this.getDescription().getVersion() + " is activated");
    }

    public void onDisable() {
        if (set != null) {
            set.deleteMarkerSet();
            set = null;
        }
        resareas.clear();
        stop = true;
    }

}
