package org.streetpacman.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.streetpacman.DMStreetPacman;
import org.streetpacman.util.DMUtils;

import android.os.Handler;
import android.util.Log;

public final class DMCore {
	public static DMCore core;
	final Handler mHandler = new Handler();
	public List<Integer> alGames;
	public List<Integer> alMaps;
	public static List<DMPhoneState> phoneStates = new ArrayList<DMPhoneState>();
	public static Map<Integer, DMPhoneState> phoneStatesMap = new HashMap<Integer, DMPhoneState>();

	public static DMMap map;
	public static DMPhone myPhone;
	public static DMPhoneState myPhoneState = new DMPhoneState();
	public volatile int myPhoneIndex = 0; // in phoneStates
	public volatile boolean powerMode = false;
	public volatile boolean allowUpdate = true;

	public DMCore(String deviceId) {
		myPhone = new DMPhone();
		map = new DMMap();
		myPhone.phoneToken = "a_" + deviceId;
		core = this;
	}

	public static DMCore self() {
		return core;
	}

	public void net(final int api, final Runnable rTrue, final Runnable rFalse) {
		Thread t = new Thread() {
			public void run() {
				final JSONObject json = DMNet
						.call(api, myPhone.getJSONFor(api));
				mHandler.post(new Runnable() {
					public void run() {
						try {
							if (json == null) {
								Log.i(DMConstants.TAG,
										"net json == null, network problem? api "
												+ api);
								rFalse.run();
							} else {
								switch (api) {
								case DMConstants.update:
									if (allowUpdate) {
										update(json);
									}
									break;
								case DMConstants.find_games:
									find_games(json);
									break;
								case DMConstants.find_maps:
									find_maps(json);
									break;
								case DMConstants.new_game:
									new_game(json);
									break;
								case DMConstants.join_game:
									join_game(json);
									break;
								case DMConstants.update_phone_settings:
									update_phone_settings(json);
									break;
								}
								rTrue.run();
							}
						} catch (JSONException e) {
							Log.i(DMConstants.TAG,
									"net json!=null, JSONException api " + api);
							e.printStackTrace();
							rFalse.run();
						}
					}
				});
			}
		};
		t.start();
	}

	// post API invoke before rTrue
	public void update_phone_settings(JSONObject json) throws JSONException {
		myPhone.phone = json.getInt("phone");
	}

	public void find_games(JSONObject json) throws JSONException {
		JSONArray jsonArray = json.getJSONArray("items");
		alGames = new ArrayList<Integer>();
		for (int i = 0; i < jsonArray.length(); i++) {
			alGames.add(jsonArray.getJSONObject(i).getInt("id"));
		}
	}

	public void find_maps(JSONObject json) throws JSONException {
		JSONArray jsonArray = json.getJSONArray("items");
		alMaps = new ArrayList<Integer>();
		for (int i = 0; i < jsonArray.length(); i++) {
			alMaps.add(jsonArray.getJSONObject(i).getInt("id"));
		}
	}

	private void initGame(JSONObject json) throws JSONException {
		JSONObject mapInfo = json.getJSONObject("mapInfo");
		map.dotPoints = DMUtils.JSONArray2GeoPoints(mapInfo
				.getJSONArray("dotPoints"));
		map.basePoints = DMUtils.JSONArray2GeoPoints(mapInfo
				.getJSONArray("basePoints"));
		map.powerPelletPoints = DMUtils.JSONArray2GeoPoints(mapInfo
				.getJSONArray("powerPelletPoints"));
		map.buildPoints();
	}

	public void new_game(JSONObject json) throws JSONException {
		myPhone.game = json.getInt("game");
		myPhone.gameToken = json.getString("gameToken");
		initGame(json);
	}

	public void join_game(JSONObject json) throws JSONException {
		initGame(json);
	}

	public void update(JSONObject json) throws JSONException {
		powerMode = json.getBoolean("powerMode");
		updatePhoneStates(json.getJSONArray("phoneStates"));
		updateEvents(json.getJSONArray("events"));
	}

	// Events
	public void updatePhoneStates(JSONArray jsonArray) throws JSONException {
		synchronized (phoneStates) {
			phoneStates.clear();
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONObject json = jsonArray.getJSONObject(i);
				DMPhoneState dmPhoneState = new DMPhoneState();
				dmPhoneState.phone = json.getInt("phone");
				dmPhoneState.lat = new Double(json.getString("lat"));
				dmPhoneState.lng = new Double(json.getString("lng"));
				dmPhoneState.idle = json.getInt("idle");
				dmPhoneState.alive = json.getBoolean("alive");
				phoneStates.add(dmPhoneState);
				if (dmPhoneState.phone == myPhone.phone) {
					myPhoneIndex = i;
					myPhoneState = dmPhoneState;
				}
				// phoneStatesMap
				phoneStatesMap.put(dmPhoneState.phone, dmPhoneState);
			}
		}
	}

	public void updateEvents(JSONArray jsonArray) throws JSONException {
		for (int i = 0; i < jsonArray.length(); i++) {
			JSONObject json = jsonArray.getJSONObject(i);
			switch (json.getInt("type")) {
			case DMConstants.OHHAI_EVENT:
				int phone = json.getInt("phone");
				String name = json.getString("name");
				break;
			case DMConstants.PHONE_EATEN_EVENT:
				int eater = json.getInt("eater");
				int eatee = json.getInt("eatee");
				killPhone(eatee);
				break;
			case DMConstants.ITEM_EATEN_EVENT:
				JSONArray kArray = json.getJSONArray("k");
				String kType = kArray.getString(0);
				int x = (int) (new Double(kArray.getString(1)) * 1E6);
				int y = (int) (new Double(kArray.getString(2)) * 1E6);
				if (kType == "p") {
					map.killPowerPellet(x, y);
					powerMode = true;
				}
				if (kType == "d") {
					map.killDot(x, y);
				}
				break;
			case DMConstants.GAME_OVER:
				int reason = json.getInt("reason");
				if (reason == DMConstants.GAMEOVER_PACMAN_WINS) {

				}
				if (reason == DMConstants.GAMEOVER_PACMAN_LOSES) {

				}
			}
			myPhone.id__gte = json.getInt("i");
			int t = json.getInt("t");
		}

	}

	private void killPhone(int phone) {
		phoneStatesMap.get(phone).status = DMConstants.PHONE_KILLED;
	}

	public int getAnimIndex(int ctx) {		
		if (!myPhoneState.alive){
			return DMConstants.DEAD[ctx];
		}
		if (powerMode) {
			return DMConstants.POWERMODE[ctx];
		}
		return ctx;
	}

}
