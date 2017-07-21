package com.boydti.phider;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.ChunkCoordIntPair;
import com.comphenix.protocol.wrappers.MultiBlockChangeInfo;
import com.comphenix.protocol.wrappers.WrappedBlockData;
import com.intellectualcrafters.plot.PS;
import com.intellectualcrafters.plot.object.Location;
import com.intellectualcrafters.plot.object.Plot;
import com.intellectualcrafters.plot.object.PlotArea;
import com.intellectualcrafters.plot.object.PlotBlock;
import com.intellectualcrafters.plot.object.PlotPlayer;
import com.plotsquared.bukkit.util.BukkitUtil;

public class PacketHandler {
	public static ProtocolManager manager;
	private static HashMap<String, Integer> worldHeight = new HashMap<>();
	private static int[][] markShape = { { 7, 4 }, { 8, 4 }, { 9, 4 }, { 6, 5 }, { 6, 6 }, { 10, 5 }, { 10, 6 },
			{ 10, 7 }, { 9, 8 }, { 8, 9 }, { 8, 10 }, { 8, 12 } };

	public PacketHandler(Main main) {
		manager = ProtocolLibrary.getProtocolManager();
		manager.addPacketListener(
				new PacketAdapter(main, ListenerPriority.NORMAL, PacketType.Play.Server.BLOCK_CHANGE) {
					@Override
					public void onPacketSending(PacketEvent event) {
						Player player = event.getPlayer();
						if (player.hasPermission("plots.plothider.bypass")) {
							return;
						}
						PlotPlayer pp = BukkitUtil.getPlayer(player);
						String world = pp.getLocation().getWorld();
						if (!PS.get().hasPlotArea(world)) { // Not a plot area
							return;
						}
						PacketContainer packet = event.getPacket();
						StructureModifier<BlockPosition> positions = packet.getBlockPositionModifier();
						BlockPosition position = positions.read(0);
						int x = position.getX(), y = position.getY(), z = position.getZ();
						Location loc = new Location(world, x, 0, z);
						Plot plot = loc.getOwnedPlot();
						if (plot == null || plot.isAdded(pp.getUUID()) || !Main.HIDE_FLAG.isTrue(plot)) {
							return;
						}

						packet = event.getPacket().shallowClone();
						StructureModifier<WrappedBlockData> blocks = packet.getBlockData();
						WrappedBlockData blockData = blocks.read(0);
						int plotHeight = getWorldHeight(player, world);
						boolean[][] marked = markShape(16, markShape);
						int dx = (x % 16) >= 0 ? (x % 16) : (x % 16 + 16),
								dz = (z % 16) >= 0 ? (z % 16) : (z % 16 + 16);

						if (y > plotHeight)
							blockData.setTypeAndData(Material.AIR, 0);
						else if (y == plotHeight && marked[dx][dz])
							blockData.setTypeAndData(Material.CONCRETE, 14);
						else
							blockData.setTypeAndData(Material.STONE, 0);
						blocks.write(0, blockData);
						event.setPacket(packet);
					}
				});

		manager.addPacketListener(
				new PacketAdapter(main, ListenerPriority.NORMAL, PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
					@Override
					public void onPacketSending(PacketEvent event) {
						Player player = event.getPlayer();
						if (player.hasPermission("plots.plothider.bypass")) {
							return;
						}
						PlotPlayer pp = BukkitUtil.getPlayer(player);
						String world = pp.getLocation().getWorld();
						if (!PS.get().hasPlotArea(world)) { // Not a plot area
							return;
						}
						PacketContainer packet = event.getPacket();
						StructureModifier<ChunkCoordIntPair> chunkArray = packet.getChunkCoordIntPairs();
						ChunkCoordIntPair chunk = chunkArray.read(0);
						int cx = chunk.getChunkX();
						int cz = chunk.getChunkZ();
						int bx = cx << 4;
						int bz = cz << 4;
						Location corner1 = new Location(world, bx, 0, bz);
						Location corner2 = new Location(world, bx + 15, 0, bz);
						Location corner3 = new Location(world, bx, 0, bz + 15);
						Location corner4 = new Location(world, bx + 15, 0, bz + 15);
						Plot plot1 = corner1.getOwnedPlot();
						Plot plot2 = corner2.getOwnedPlot();
						Plot plot3 = corner3.getOwnedPlot();
						Plot plot4 = corner4.getOwnedPlot();
						plot1 = (plot1 != null && (plot1.isDenied(pp.getUUID())
								|| (!plot1.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot1)))) ? plot1 : null;
						plot2 = (plot2 != null && (plot2.isDenied(pp.getUUID())
								|| (!plot2.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot2)))) ? plot2 : null;
						plot3 = (plot3 != null && (plot3.isDenied(pp.getUUID())
								|| (!plot3.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot3)))) ? plot3 : null;
						plot4 = (plot4 != null && (plot4.isDenied(pp.getUUID())
								|| (!plot4.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot4)))) ? plot4 : null;
						if (plot1 == null && plot2 == null && plot3 == null && plot4 == null) {
							// No plots to hide
							return;
						}
						if (plot1 == plot4 && plot1 != null) {
							// Not allowed to see the entire chunk
							event.setCancelled(true);
							return;
						}

						packet = event.getPacket().shallowClone();
						StructureModifier<MultiBlockChangeInfo[]> changeArray = packet.getMultiBlockChangeInfoArrays();
						int plotHeight = getWorldHeight(player, world);
						boolean[][] marked = markShape(16, markShape);
						WrappedBlockData airblock = WrappedBlockData.createData(Material.AIR, (byte) 0);
						WrappedBlockData groundblock = WrappedBlockData.createData(Material.STONE, (byte) 0);
						WrappedBlockData markblock = WrappedBlockData.createData(Material.CONCRETE, (byte) 14);

						// Hide some of the blocks (but maybe not all)
						List<MultiBlockChangeInfo> changes = new ArrayList<>(Arrays.asList(changeArray.read(0)));
						Iterator<MultiBlockChangeInfo> iter = changes.iterator();
						Plot denied = plot1 != null ? plot1 : plot2 != null ? plot2 : plot3 != null ? plot3 : plot4;
						PlotArea area = denied.getArea();
						while (iter.hasNext()) {
							MultiBlockChangeInfo change = iter.next();
							int x = change.getAbsoluteX();
							int y = change.getY();
							int z = change.getAbsoluteZ();
							Plot current = area.getOwnedPlot(new Location(world, x, 0, z));
							if (current == null) {
								continue;
							}
							if (y > plotHeight)
								change.setData(airblock);
							else if (y == plotHeight && marked[x][z])
								change.setData(markblock);
							else
								change.setData(groundblock);
							if (current == plot1 || current == plot2 || current == plot3 || current == plot4) {
								iter.remove();
							}
						}
						if (changes.size() == 0) {
							event.setCancelled(true);
							return;
						}
						// changeArray.write(0, changes.toArray(new
						// MultiBlockChangeInfo[changes.size()]));
						event.setPacket(packet);
					}
				});

		manager.addPacketListener(new PacketAdapter(main, ListenerPriority.NORMAL, PacketType.Play.Server.MAP_CHUNK) {
			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();
				if (player.hasPermission("plots.plothider.bypass")) {
					return;
				}
				PlotPlayer pp = BukkitUtil.getPlayer(player);
				String world = pp.getLocation().getWorld();
				if (!PS.get().hasPlotArea(world)) { // Not a plot area
					return;
				}
				PacketContainer packet = event.getPacket();
				StructureModifier<Integer> ints = packet.getIntegers();
				StructureModifier<byte[]> byteArray = packet.getByteArrays();
				int cx = ints.read(0);
				int cz = ints.read(1);
				int bx = cx << 4;
				int bz = cz << 4;
				Location corner1 = new Location(world, bx, 0, bz);
				Location corner2 = new Location(world, bx + 15, 0, bz);
				Location corner3 = new Location(world, bx, 0, bz + 15);
				Location corner4 = new Location(world, bx + 15, 0, bz + 15);
				Plot plot1 = corner1.getOwnedPlot();
				Plot plot2 = corner2.getOwnedPlot();
				Plot plot3 = corner3.getOwnedPlot();
				Plot plot4 = corner4.getOwnedPlot();
				plot1 = (plot1 != null && (plot1.isDenied(pp.getUUID())
						|| (!plot1.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot1)))) ? plot1 : null;
				plot2 = (plot2 != null && (plot2.isDenied(pp.getUUID())
						|| (!plot2.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot2)))) ? plot2 : null;
				plot3 = (plot3 != null && (plot3.isDenied(pp.getUUID())
						|| (!plot3.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot3)))) ? plot3 : null;
				plot4 = (plot4 != null && (plot4.isDenied(pp.getUUID())
						|| (!plot4.isAdded(pp.getUUID()) && Main.HIDE_FLAG.isTrue(plot4)))) ? plot4 : null;
				if (plot1 == null && plot2 == null && plot3 == null && plot4 == null) {
					// No plots to hide
					return;
				}
				// Not allowed to see part of the chunk
				Plot denied = plot1 != null ? plot1 : plot2 != null ? plot2 : plot3 != null ? plot3 : plot4;
				PlotArea area = denied.getArea();
				PlotBlock airblock = new PlotBlock((short) 0, (byte) 0);
				PlotBlock groundblock = new PlotBlock((short) 1, (byte) 0);
				PlotBlock markblock = new PlotBlock((short) 251, (byte) 14);

				packet = event.getPacket().shallowClone();
				byteArray = packet.getByteArrays();
				byte[] sections = byteArray.read(0);
				int size = sections.length;
				try {
					byte[] biomes = Arrays.copyOfRange(sections, sections.length - 256, sections.length);
					sections = Arrays.copyOfRange(sections, 0, sections.length - 256);
					List<BlockStorage> array = new ArrayList<>();
					while (sections.length > 0) {
						if (sections[0] < 0) {
							break;
						}
						BlockStorage storage = new BlockStorage(sections);
						array.add(storage);
						sections = Arrays.copyOfRange(sections,
								Math.min(storage.getSize() + storage.getLight().length, sections.length),
								sections.length);
					}
					// Trim chunk
					int plotHeight = getWorldHeight(player, world);
					boolean[][] marked = markShape(16, markShape);
					int chunkmask = ints.read(2); // Primary Bit Mask
					List<Integer> chunlist = getChunkHeight(chunkmask);

					for (int x = 0; x < 16; x++) {
						for (int z = 0; z < 16; z++) {
							Location loc = new Location(world, bx + x, 0, bz + z);
							Plot current = area.getOwnedPlot(loc);
							if (current == null) {
								continue;
							}
							if (current == plot1 || current == plot2 || current == plot3 || current == plot4) {
								for (int i = 0; i < array.size(); i++) {
									BlockStorage section = array.get(i);
									int chunkbaseheight = 16 * chunlist.get(i);
									for (int y = 0; y < 16; y++) {
										int height = chunkbaseheight + y;
										if (height > plotHeight)
											section.set(x, y, z, airblock);
										else if (height == plotHeight && marked[x][z])
											section.set(x, y, z, markblock);
										else
											section.set(x, y, z, groundblock);
									}
								}
							}
						}
					}
					// Write
					ByteArrayOutputStream baos = new ByteArrayOutputStream(size);
					for (BlockStorage section : array) {
						section.write(baos);
					}
					baos.write(sections);
					baos.write(biomes);
					byteArray.write(0, baos.toByteArray());
					event.setPacket(packet);
				} catch (Throwable e) {
					e.printStackTrace();
				}

			}
		});
	}

	private boolean[][] markShape(int size, int[][] marked) {
		boolean[][] mark = new boolean[size][size];
		for (int[] array : marked) {
			mark[array[0]][array[1]] = true;
		}
		return mark;
	}

	private List<Integer> getChunkHeight(int chunkmask) {
		List<Integer> list = new ArrayList<>();
		int mask = 1, MAX_HEIGHT = 256, CHUNK_HEIGHT = 16;
		for (int i = 0; i < MAX_HEIGHT / CHUNK_HEIGHT; i++) {
			if (mask == (mask & chunkmask)) {
				list.add(i);
			}
			mask <<= 1;
		}
		return list;
	}

	private int getWorldHeight(Player player, String world) {
		if (!worldHeight.containsKey(world)) {
			worldHeight.put(world, PS.get().worlds.getInt("worlds." + player.getWorld().getName() + ".plot.height"));
		}
		return worldHeight.get(world);
	}
}
