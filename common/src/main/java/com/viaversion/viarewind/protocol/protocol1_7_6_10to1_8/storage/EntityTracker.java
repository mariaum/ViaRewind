/*
 * This file is part of ViaRewind - https://github.com/ViaVersion/ViaRewind
 * Copyright (C) 2016-2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.viaversion.viarewind.protocol.protocol1_7_6_10to1_8.storage;

import com.viaversion.viarewind.protocol.protocol1_7_6_10to1_8.Protocol1_7_6_10To1_8;
import com.viaversion.viarewind.api.minecraft.EntityModel;
import com.viaversion.viarewind.utils.PacketUtil;
import com.viaversion.viaversion.api.connection.StoredObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.entity.ClientEntityIdChangeListener;
import com.viaversion.viaversion.api.minecraft.entities.Entity1_10Types;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.type.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EntityTracker extends StoredObject implements ClientEntityIdChangeListener {
	private final Map<Integer, Entity1_10Types.EntityType> entityMap = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> vehicles = new ConcurrentHashMap<>();
	private final Map<Integer, EntityModel> entityReplacements = new ConcurrentHashMap<>();
	private final Map<Integer, UUID> playersByEntityId = new HashMap<>();
	private final Map<UUID, Integer> playersByUniqueId = new HashMap<>();
	private final Map<UUID, Item[]> playerEquipment = new HashMap<>();
	private int gamemode = 0;
	private int playerId = -1;
	private int spectating = -1;
	private int dimension = 0;

	public EntityTracker(UserConnection user) {
		super(user);
	}

	public void removeEntity(int entityId) {
		entityMap.remove(entityId);
		if (entityReplacements.containsKey(entityId)) {
			entityReplacements.remove(entityId).deleteEntity();
		}
		if (playersByEntityId.containsKey(entityId)) {
			UUID playerId = playersByEntityId.remove(entityId);
			playersByUniqueId.remove(playerId);
			playerEquipment.remove(playerId);
		}
	}

	public void addPlayer(Integer entityId, UUID uuid) {
		playersByUniqueId.put(uuid, entityId);
		playersByEntityId.put(entityId, uuid);
	}

	public UUID getPlayerUUID(int entityId) {
		return playersByEntityId.get(entityId);
	}

	public int getPlayerEntityId(UUID uuid) {
		return playersByUniqueId.getOrDefault(uuid, -1);
	}

	public Item getPlayerEquipment(UUID uuid, int slot) {
		Item[] items = playerEquipment.get(uuid);
		if (items == null || slot < 0 || slot >= items.length) return null;
		return items[slot];
	}

	public void setPlayerEquipment(UUID uuid, Item equipment, int slot) {
		// Please note that when referring to the client player, it has an Item[4] array
		Item[] items = playerEquipment.computeIfAbsent(uuid, it -> new Item[5]);
		if (slot < 0 || slot >= items.length) return;
		items[slot] = equipment;
	}

	public Map<Integer, Entity1_10Types.EntityType> getEntityMap() {
		return this.entityMap;
	}

	public void addEntityReplacement(EntityModel entityModel) {
		entityReplacements.put(entityModel.getEntityId(), entityModel);
	}

	public EntityModel getEntityReplacement(int entityId) {
		return entityReplacements.get(entityId);
	}

	public int getVehicle(int passengerId) {
		for (Map.Entry<Integer, Integer> vehicle : vehicles.entrySet()) {
			if (vehicle.getValue() == passengerId) return vehicle.getValue();
		}
		return -1;
	}

	public int getPassenger(int vehicleId) {
		return vehicles.getOrDefault(vehicleId, -1);
	}

	public void setPassenger(int vehicleId, int passengerId) {
		if (vehicleId == this.spectating && this.spectating != this.playerId) {
			try {
				PacketWrapper sneakPacket = PacketWrapper.create(0x0B, null, getUser());
				sneakPacket.write(Type.VAR_INT, playerId);
				sneakPacket.write(Type.VAR_INT, 0);  //Start sneaking
				sneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

				PacketWrapper unsneakPacket = PacketWrapper.create(0x0B, null, getUser());
				unsneakPacket.write(Type.VAR_INT, playerId);
				unsneakPacket.write(Type.VAR_INT, 1);  //Stop sneaking
				unsneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

				PacketUtil.sendToServer(sneakPacket, Protocol1_7_6_10To1_8.class, true, true);

				setSpectating(playerId);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		if (vehicleId == -1) {
			int oldVehicleId = getVehicle(passengerId);
			vehicles.remove(oldVehicleId);
		} else if (passengerId == -1) {
			vehicles.remove(vehicleId);
		} else {
			vehicles.put(vehicleId, passengerId);
		}
	}

	public int getSpectating() {
		return spectating;
	}

	public boolean setSpectating(int spectating) {
		if (spectating != this.playerId && getPassenger(spectating) != -1) {

			PacketWrapper sneakPacket = PacketWrapper.create(0x0B, null, getUser());
			sneakPacket.write(Type.VAR_INT, playerId);
			sneakPacket.write(Type.VAR_INT, 0);  //Start sneaking
			sneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

			PacketWrapper unsneakPacket = PacketWrapper.create(0x0B, null, getUser());
			unsneakPacket.write(Type.VAR_INT, playerId);
			unsneakPacket.write(Type.VAR_INT, 1);  //Stop sneaking
			unsneakPacket.write(Type.VAR_INT, 0);  //Action Parameter

			PacketUtil.sendToServer(sneakPacket, Protocol1_7_6_10To1_8.class, true, true);

			setSpectating(this.playerId);
			return false;  //Entity has Passenger
		}

		if (this.spectating != spectating && this.spectating != this.playerId) {
			PacketWrapper unmount = PacketWrapper.create(0x1B, null, this.getUser());
			unmount.write(Type.INT, this.playerId);
			unmount.write(Type.INT, -1);
			unmount.write(Type.BOOLEAN, false);
			PacketUtil.sendPacket(unmount, Protocol1_7_6_10To1_8.class);
		}
		this.spectating = spectating;
		if (spectating != this.playerId) {
			PacketWrapper mount = PacketWrapper.create(0x1B, null, this.getUser());
			mount.write(Type.INT, this.playerId);
			mount.write(Type.INT, this.spectating);
			mount.write(Type.BOOLEAN, false);
			PacketUtil.sendPacket(mount, Protocol1_7_6_10To1_8.class);
		}
		return true;
	}

	public int getGamemode() {
		return gamemode;
	}

	public void setGamemode(int gamemode) {
		this.gamemode = gamemode;
	}

	public int getPlayerId() {
		return playerId;
	}

	public void setPlayerId(int playerId) {
		this.playerId = this.spectating = playerId;
	}

	public void clearEntities() {
		entityMap.clear();
		entityReplacements.clear();
		vehicles.clear();
	}

	public int getDimension() {
		return dimension;
	}

	public void setDimension(int dimension) {
		this.dimension = dimension;
	}

	@Override
	public void setClientEntityId(int playerEntityId) {
		if (this.spectating == this.playerId) {
			this.spectating = playerEntityId;
		}
		entityMap.remove(this.playerId);
		this.playerId = playerEntityId;
		entityMap.put(this.playerId, Entity1_10Types.EntityType.ENTITY_HUMAN);
	}
}
