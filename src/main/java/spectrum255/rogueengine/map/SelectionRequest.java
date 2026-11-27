package spectrum255.rogueengine.map;

import java.util.List;
import java.util.Objects;

import net.minecraft.resources.Identifier;

public record SelectionRequest(List<Identifier> sceneIds, List<Identifier> tags) {
	public SelectionRequest {
		sceneIds = List.copyOf(sceneIds);
		tags = List.copyOf(tags);
	}

	public static SelectionRequest all() {
		return new SelectionRequest(List.of(), List.of());
	}

	public MapPool.SelectionRange toRange() {
		return new MapPool.SelectionRange(sceneIds, tags);
	}

	public boolean isAll() {
		return sceneIds.isEmpty() && tags.isEmpty();
	}
}
