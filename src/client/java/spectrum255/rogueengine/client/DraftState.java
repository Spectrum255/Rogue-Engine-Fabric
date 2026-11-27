package spectrum255.rogueengine.client;

import spectrum255.rogueengine.tool.ConfigDraft;
import spectrum255.rogueengine.tool.DatapackAuthoringService;

public final class DraftState {
	private final DatapackAuthoringService service;
	private volatile ConfigDraft draft;

	public DraftState(DatapackAuthoringService service, ConfigDraft draft) {
		this.service = service;
		this.draft = draft;
	}

	public DatapackAuthoringService service() {
		return service;
	}

	public ConfigDraft draft() {
		return draft;
	}

	public void draft(ConfigDraft draft) {
		this.draft = draft;
	}
}
