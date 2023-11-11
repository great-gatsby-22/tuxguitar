package org.herac.tuxguitar.app.view.dialog.track;

import java.util.ArrayList;
import java.util.List;

import org.herac.tuxguitar.app.TuxGuitar;
import org.herac.tuxguitar.app.system.icons.TGIconManager;
import org.herac.tuxguitar.app.ui.TGApplication;
import org.herac.tuxguitar.app.util.TGMessageDialogUtil;
import org.herac.tuxguitar.app.util.TGMusicKeyUtils;
import org.herac.tuxguitar.app.view.controller.TGViewContext;
import org.herac.tuxguitar.app.view.util.TGDialogUtil;
import org.herac.tuxguitar.document.TGDocumentContextAttributes;
import org.herac.tuxguitar.editor.action.TGActionProcessor;
import org.herac.tuxguitar.editor.action.track.TGChangeTrackTuningAction;
import org.herac.tuxguitar.song.helpers.tuning.TuningGroup;
import org.herac.tuxguitar.song.helpers.tuning.TuningPreset;
import org.herac.tuxguitar.editor.util.TGSyncProcessLocked;
import org.herac.tuxguitar.song.managers.TGSongManager;
import org.herac.tuxguitar.song.models.TGSong;
import org.herac.tuxguitar.song.models.TGString;
import org.herac.tuxguitar.song.models.TGTrack;
import org.herac.tuxguitar.ui.UIFactory;
import org.herac.tuxguitar.ui.event.UIModifyEvent;
import org.herac.tuxguitar.ui.event.UIModifyListener;
import org.herac.tuxguitar.ui.event.UIMouseDoubleClickListener;
import org.herac.tuxguitar.ui.event.UIMouseEvent;
import org.herac.tuxguitar.ui.event.UISelectionEvent;
import org.herac.tuxguitar.ui.event.UISelectionListener;
import org.herac.tuxguitar.ui.layout.UITableLayout;
import org.herac.tuxguitar.ui.widget.*;

public class TGTrackTuningDialog {

	private static final String[] NOTE_NAMES = TGMusicKeyUtils.getSharpKeyNames(TGMusicKeyUtils.PREFIX_TUNING);
	private static final float MINIMUM_BUTTON_WIDTH = 80;
	private static final float MINIMUM_BUTTON_HEIGHT = 25;
	private static final int MAX_OCTAVES = 10;
	private static final int MAX_NOTES = 12;

	private TGViewContext context;
	private UIWindow dialog;
	
	private TuningGroup allTuningsGroup;	// group #0 = custom tuning presets, other groups = TuxGuitar's tuning presets

	private List<TGTrackTuningModel> initialTuning;
	private List<TGTrackTuningModel> tuning;
	private UITable<TGTrackTuningModel> tuningTable;
	private UICheckBox stringTransposition;
	private UICheckBox stringTranspositionTryKeepString;
	private UICheckBox stringTranspositionApplyToChords;
	private UISpinner offsetSpinner;
	private UIButton buttonEdit;
	private UIButton buttonDelete;
	private UIButton buttonMoveUp;
	private UIButton buttonMoveDown;
	private UIPanel presetsPanel;
	private UIButton buttonPresetSave;
	private UIButton buttonPresetSaveAs;
	private UIButton buttonPresetDelete;
	private UITextField newPresetName;
	private TuningPreset currentSelectedPreset;
	private boolean isNewPreset;
	
	public TGTrackTuningDialog(TGViewContext context) {
		this.context = context;
		TuningGroup tgTunings = TuxGuitar.getInstance().getTuningManager().getTgTuningsGroup();
		TuningGroup customTunings = TuxGuitar.getInstance().getTuningManager().getCustomTuningsGroup();
		customTunings.setName(TuxGuitar.getProperty("tuning.preset.select"));
		allTuningsGroup = new TuningGroup();
		allTuningsGroup.addGroup(customTunings);
		for (TuningGroup group : tgTunings.getGroups()) {
			allTuningsGroup.addGroup(group);
		}
	}

	public void show() {
		TGSongManager songManager = this.findSongManager();
		TGTrack track = this.findTrack();
		
		if(!songManager.isPercussionChannel(track.getSong(), track.getChannelId())) {
			this.tuning = getTuningFromTrack(track);
			this.initialTuning = getTuningFromTrack(track);
			
			UIFactory factory = this.getUIFactory();
			UIWindow parent = this.context.getAttribute(TGViewContext.ATTRIBUTE_PARENT);
			UITableLayout dialogLayout = new UITableLayout();
			
			this.dialog = factory.createWindow(parent, true, false);
			this.dialog.setLayout(dialogLayout);
			this.dialog.setText(TuxGuitar.getProperty("tuning"));
			
			UITableLayout leftPanelLayout = new UITableLayout();
			UILegendPanel leftPanel = factory.createLegendPanel(this.dialog);
			leftPanel.setLayout(leftPanelLayout);
			leftPanel.setText(TuxGuitar.getProperty("tuning.strings"));
			dialogLayout.set(leftPanel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			
			UITableLayout rightPanelLayout = new UITableLayout();
			UILegendPanel rightPanel = factory.createLegendPanel(this.dialog);
			rightPanel.setLayout(rightPanelLayout);
			rightPanel.setText(TuxGuitar.getProperty("options"));
			dialogLayout.set(rightPanel, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
			
			UITableLayout bottomPanelLayout = new UITableLayout(0f);
			UIPanel bottomPanel = factory.createPanel(this.dialog, false);
			bottomPanel.setLayout(bottomPanelLayout);
			dialogLayout.set(bottomPanel, 2, 1, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, true, 1, 2);
			
			this.initTuningTable(leftPanel);
			this.initTuningOptions(rightPanel, track);
			this.initButtons(bottomPanel);
			this.updateTuningControls();
			
			TGDialogUtil.openDialog(this.dialog, TGDialogUtil.OPEN_STYLE_CENTER | TGDialogUtil.OPEN_STYLE_PACK);
		}
	}
	
	private TuningPreset findTuningInGroup(List<TGTrackTuningModel> tuningModel, TuningGroup group) {
		if (group.getGroups() != null && !group.getGroups().isEmpty()) {
			for (TuningGroup searchGroup : group.getGroups() ) {
				TuningPreset found = findTuningInGroup(tuning, searchGroup);
				if (found != null) {
					return(found);
				}
			}
		}
		else {
			for (TuningPreset preset : group.getTunings()) {
				if (areTuningsEqual(tuningModel, preset)) {
					return(preset);
				}
			}
		}
		return(null);
	}
	
	private void populateGroupsDropDown(UIDropDownSelect<TuningGroup> select, TuningGroup group, TuningGroup groupToSelect) {
		select.setIgnoreEvents(true);
		select.removeItems();
		if (group != null) {
			for (TuningGroup subGroup : group.getGroups()) {
				boolean wasEmpty = (select.getItemCount() == 0);
				select.addItem(new UISelectItem<TuningGroup>(subGroup.getName(), subGroup));
				if ((wasEmpty && groupToSelect==null) || subGroup==groupToSelect) {
					select.setSelectedValue(subGroup);
				}
			}
		}
		select.setEnabled(select.getItemCount() > 0);
		select.setIgnoreEvents(false);
	}
	
	private void populateTuningsDropDown(UIDropDownSelect<TuningPreset> select, TuningGroup group, TuningPreset presetToSelect) {
		select.setIgnoreEvents(true);
		select.removeItems();
		if (group != null) {
			for (TuningPreset tuning : group.getTunings()) {
				select.addItem(new UISelectItem<TuningPreset>(tuningPresetLabel(tuning), tuning));
			}
			if (presetToSelect != null) {
				select.setSelectedValue(presetToSelect);
				this.currentSelectedPreset = presetToSelect;
			}
			else {
				this.currentSelectedPreset = null;
			}
		}
		select.setEnabled(select.getItemCount() > 0);
		select.setIgnoreEvents(false);
	}
	
	// add a suffix to tuning preset name, to show all notes
	private String tuningPresetLabel(TuningPreset preset) {
		StringBuilder label = new StringBuilder();
		label.append(preset.getName()).append(" - ");
		int[] values = preset.getValues();
		for(int i = 0 ; i < values.length; i ++) {
			if( i > 0 ) {
				label.append(" ");
			}
		label.append(getValueLabel(values[values.length - i - 1]));
		}
		return label.toString();
	}
	
	private void initTuningTable(UILayoutContainer parent) {
		UIFactory factory = this.getUIFactory();
		UITableLayout parentLayout = (UITableLayout) parent.getLayout();
		
		UITableLayout panelLayout = new UITableLayout();
		UIPanel panel = factory.createPanel(parent, false);
		panel.setLayout(panelLayout);
		parentLayout.set(panel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		
		presetsPanel = factory.createPanel(panel, false);
		panelLayout.set(presetsPanel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, false);
		panelLayout.set(presetsPanel, UITableLayout.PACKED_HEIGHT, 120f);
		
		this.tuningTable = factory.createTable(panel, true);
		this.tuningTable.setColumns(2);
		this.tuningTable.setColumnName(0, TuxGuitar.getProperty("tuning.label"));
		this.tuningTable.setColumnName(1, TuxGuitar.getProperty("tuning.value"));
		this.tuningTable.addMouseDoubleClickListener(new UIMouseDoubleClickListener() {
			public void onMouseDoubleClick(UIMouseEvent event) {
				TGTrackTuningDialog.this.onEditTuningModel();
			}
		});
		panelLayout.set(this.tuningTable, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, true);
		panelLayout.set(this.tuningTable, UITableLayout.PACKED_WIDTH, 320f);
		panelLayout.set(this.tuningTable, UITableLayout.PACKED_HEIGHT, 200f);
		
		UITableLayout buttonsLayout = new UITableLayout(0f);
		UIPanel buttonsPanel = factory.createPanel(panel, false);
		buttonsPanel.setLayout(buttonsLayout);
		panelLayout.set(buttonsPanel, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, null, null, 0f);
		
		UIButton buttonAdd = factory.createButton(buttonsPanel);
		buttonAdd.setImage(TGIconManager.getInstance(this.context.getContext()).getListAdd());
		buttonAdd.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.onAddTuningModel();
			}
		});
		
		buttonEdit = factory.createButton(buttonsPanel);
		buttonEdit.setImage(TGIconManager.getInstance(this.context.getContext()).getListEdit());
		buttonEdit.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.onEditTuningModel();
			}
		});	
		
		buttonMoveUp = factory.createButton(buttonsPanel);
		buttonMoveUp.setImage(TGIconManager.getInstance(this.context.getContext()).getArrowUp());
		buttonMoveUp.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.moveString(-1);
			}
		});
		
		buttonMoveDown = factory.createButton(buttonsPanel);
		buttonMoveDown.setImage(TGIconManager.getInstance(this.context.getContext()).getArrowDown());
		buttonMoveDown.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.moveString(1);
			}
		});
		
		buttonDelete = factory.createButton(buttonsPanel);
		buttonDelete.setImage(TGIconManager.getInstance(this.context.getContext()).getListRemove());
		buttonDelete.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.onRemoveTuningModel();
			}
		});
		
		this.tuningTable.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.updateTuningButtons();
			}
		});
		
		buttonsLayout.set(buttonAdd, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_FILL, false, false);
		buttonsLayout.set(buttonDelete, 1, 2, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_FILL, false, false);
		buttonsLayout.set(buttonMoveUp, 1, 3, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, true, false);
		buttonsLayout.set(buttonMoveDown, 1, 4, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, false, false);
		buttonsLayout.set(buttonEdit, 1, 5, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, false, false);
	}
	
	// call this method:
	// - either if a tuning group drop-down list is clicked:
	//	 selectGroup is the selected group, selectPreset is null
	// - or when selecting an existing preset (e.g. load a file using default preset):
	//   selectGroup is null, selectPreset is the selected preset
	// - or when selecting an unknown preset (e.g. load a file using unknown preset, or edit current tuning):
	//   selectGroup and selectPreset are null
	private void updatePresetsPanel(TuningGroup selectGroup, TuningPreset selectPreset) {
		// error case
		if (selectGroup != null && selectPreset != null) return;
		
		UIFactory factory = this.getUIFactory();
		TuningGroup group = null;
		int nDropDown=0;
		
		this.isNewPreset = (selectGroup==null && selectPreset==null);
		
		// build list of successive groups/subGroups from root
		List<TuningGroup> selectedGroupsList;
		if (selectGroup == null) {
			if (selectPreset == null) {
				selectedGroupsList = findParentGroups(allTuningsGroup.getGroups().get(0));
			} else {
				selectedGroupsList = findParentGroups(selectPreset);
			}
		} else {
			selectedGroupsList = findParentGroups(selectGroup);
		}
		if (selectedGroupsList==null || selectedGroupsList.size()==0) {
			return;
		}
		
		// populate groups drop-down lists
		// don't update lists if currently editing a custom preset
		if (selectGroup != null || selectPreset != null || this.currentSelectedPreset==null || !isCustomTuningPreset(this.currentSelectedPreset)) {
			for (UIControl control: presetsPanel.getChildren()) {
				control.dispose();
			}
			UITableLayout presetsPanelLayout = new UITableLayout(0f);
			presetsPanel.setLayout(presetsPanelLayout);
			group = selectedGroupsList.get(0);
			while (group.getGroups().size() > 0) {
				UIDropDownSelect<TuningGroup> groupSelect = factory.createDropDownSelect(presetsPanel);
				presetsPanelLayout.set(groupSelect, 1+nDropDown, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, false);
				TuningGroup selectedGroupInList = null;
				if (selectedGroupsList.size()>nDropDown+1) {
					selectedGroupInList = selectedGroupsList.get(nDropDown+1);
				}
				this.populateGroupsDropDown(groupSelect, group, selectedGroupInList);
				groupSelect.addSelectionListener(new UISelectionListener() {
					public void onSelect(UISelectionEvent event) {
						TGTrackTuningDialog.this.onSelectGroup((UIDropDownSelect<TuningGroup>) event.getComponent());
					}
				});
				nDropDown++;
				group = selectedGroupInList!=null ? selectedGroupInList : group.getGroups().get(0);
			};
			// populate tuning presets drop-down list
			UIDropDownSelect<TuningPreset> tuningSelect = factory.createDropDownSelect(presetsPanel);
			presetsPanelLayout.set(tuningSelect, 1+nDropDown, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, false);
			TuningPreset toSelect = null;
			if (selectPreset != null) {
				toSelect = selectPreset;
			} else if (selectGroup!=null && group.getTunings().size()>0) {
				toSelect = group.getTunings().get(0);
			}
			this.populateTuningsDropDown(tuningSelect, group, toSelect);
			if (tuningSelect.getSelectedValue()!=null) {
				updateTuningTable(tuningSelect.getSelectedValue());
			}
			tuningSelect.addSelectionListener(new UISelectionListener() {
				public void onSelect(UISelectionEvent event) {
					TGTrackTuningDialog.this.onSelectPreset((UIDropDownSelect<TuningPreset>) event.getComponent());
				}
			});
			
			// create buttons if needed (custom tuning or no tuning selected)
			if (currentSelectedPreset==null || isCustomTuningPreset(currentSelectedPreset)) {
				buttonPresetDelete = factory.createButton(presetsPanel);
				buttonPresetDelete.setImage(TGIconManager.getInstance(this.context.getContext()).getListRemove());
				buttonPresetDelete.addSelectionListener(new UISelectionListener() {
					public void onSelect(UISelectionEvent event) {
						TGTrackTuningDialog.this.onDeletePreset();
					}
				});
				presetsPanelLayout.set(buttonPresetDelete, 1+nDropDown, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, false, false);
				buttonPresetSave = factory.createButton(presetsPanel);
				buttonPresetSave.setImage(TGIconManager.getInstance(this.context.getContext()).getFileSave());
				buttonPresetSave.addSelectionListener(new UISelectionListener() {
					public void onSelect(UISelectionEvent event) {
						TGTrackTuningDialog.this.onSavePreset();
					}
				});
				presetsPanelLayout.set(buttonPresetSave, 1+nDropDown, 3, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, false, false);
				newPresetName = factory.createTextField(presetsPanel);
				newPresetName.setEnabled(false);
				newPresetName.addModifyListener(new UIModifyListener() {
					public void onModify(UIModifyEvent event) {
						buttonPresetSaveAs.setEnabled(!TGTrackTuningDialog.this.newPresetName.getText().equals(""));
					}
				});
				presetsPanelLayout.set(newPresetName, 2+nDropDown, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, false);				
				buttonPresetSaveAs = factory.createButton(presetsPanel);
				buttonPresetSaveAs.setImage(TGIconManager.getInstance(this.context.getContext()).getFileSaveAs());
				buttonPresetSaveAs.addSelectionListener(new UISelectionListener() {
					public void onSelect(UISelectionEvent event) {
						TGTrackTuningDialog.this.onSavePresetAs();
					}
				});
				presetsPanelLayout.set(buttonPresetSaveAs, 2+nDropDown, 2, UITableLayout.ALIGN_RIGHT, UITableLayout.ALIGN_FILL, false, false);
			}
			presetsPanel.layout();
		}
		// enable/disable buttons if needed (custom tuning or no tuning selected)
		updatePresetsButtons();
	}
	
	private void updatePresetsButtons() {
		if (currentSelectedPreset==null || isCustomTuningPreset(currentSelectedPreset)) {
			boolean isModifiedTuning = this.isNewPreset && currentSelectedPreset!=null;
			buttonPresetDelete.setEnabled(currentSelectedPreset!=null);
			buttonPresetSave.setEnabled(isModifiedTuning);
			if (this.isNewPreset) {
				newPresetName.setEnabled(true);
				newPresetName.setIgnoreEvents(false);
			} else {
				newPresetName.setText("");
				newPresetName.setEnabled(false);
				newPresetName.setIgnoreEvents(true);
			}
			buttonPresetSaveAs.setEnabled(!newPresetName.getText().equals(""));
		}
	}
	
	private void initTuningOptions(UILayoutContainer parent, TGTrack track) {
		UIFactory factory = this.getUIFactory();
		UITableLayout parentLayout = (UITableLayout) parent.getLayout();
		
		UITableLayout panelLayout = new UITableLayout();
		UIPanel panel = factory.createPanel(parent, false);
		panel.setLayout(panelLayout);
		parentLayout.set(panel, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true);
		
		UITableLayout topLayout = new UITableLayout(0f);
		UIPanel top = factory.createPanel(panel, false);
		top.setLayout(topLayout);
		panelLayout.set(top, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_TOP, true, true, 1, 1, null, null, 0f);
		
		UITableLayout bottomLayout = new UITableLayout(0f);
		UIPanel bottom = factory.createPanel(panel, false);
		bottom.setLayout(bottomLayout);
		panelLayout.set(bottom, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_BOTTOM, true, true, 1, 1, null, null, 0f);
		
		//---------------------------------OFFSET--------------------------------
		UILabel offsetLabel = factory.createLabel(top);
		offsetLabel.setText(TuxGuitar.getProperty("tuning.offset") + ":");
		topLayout.set(offsetLabel, 1, 1, UITableLayout.ALIGN_LEFT, UITableLayout.ALIGN_CENTER, true, true);
		
		this.offsetSpinner = factory.createSpinner(top);
		this.offsetSpinner.setMinimum(TGTrack.MIN_OFFSET);
		this.offsetSpinner.setMaximum(TGTrack.MAX_OFFSET);
		this.offsetSpinner.setValue(track.getOffset());
		topLayout.set(this.offsetSpinner, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, true);
		
		//---------------------------------OPTIONS----------------------------------
		this.stringTransposition = factory.createCheckBox(bottom);
		this.stringTransposition.setText(TuxGuitar.getProperty("tuning.strings.transpose"));
		this.stringTransposition.setSelected( true );
		bottomLayout.set(this.stringTransposition, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, true);
		
		this.stringTranspositionApplyToChords = factory.createCheckBox(bottom);
		this.stringTranspositionApplyToChords.setText(TuxGuitar.getProperty("tuning.strings.transpose.apply-to-chords"));
		this.stringTranspositionApplyToChords.setSelected( true );
		bottomLayout.set(this.stringTranspositionApplyToChords, 2, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, true);
		
		this.stringTranspositionTryKeepString = factory.createCheckBox(bottom);
		this.stringTranspositionTryKeepString.setText(TuxGuitar.getProperty("tuning.strings.transpose.try-keep-strings"));
		this.stringTranspositionTryKeepString.setSelected( true );
		bottomLayout.set(this.stringTranspositionTryKeepString, 3, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_CENTER, true, true);
		
		this.stringTransposition.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				UICheckBox stringTransposition = TGTrackTuningDialog.this.stringTransposition;
				UICheckBox stringTranspositionApplyToChords = TGTrackTuningDialog.this.stringTranspositionApplyToChords;
				UICheckBox stringTranspositionTryKeepString = TGTrackTuningDialog.this.stringTranspositionTryKeepString;
				stringTranspositionApplyToChords.setEnabled((stringTransposition.isEnabled() && stringTransposition.isSelected()));
				stringTranspositionTryKeepString.setEnabled((stringTransposition.isEnabled() && stringTransposition.isSelected()));
			}
		});
	}
	
	private void initButtons(UILayoutContainer parent) {
		UIFactory factory = this.getUIFactory();
		UITableLayout parentLayout = (UITableLayout) parent.getLayout();
		
		UIButton buttonOK = factory.createButton(parent);
		buttonOK.setText(TuxGuitar.getProperty("ok"));
		buttonOK.setDefaultButton();
		buttonOK.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				new TGSyncProcessLocked(getContext().getContext(), new Runnable() {
					public void run() {
						if( TGTrackTuningDialog.this.updateTrackTuning() ) {
							TGTrackTuningDialog.this.dialog.dispose();
						}
					}
				}).process();
			}
		});
		parentLayout.set(buttonOK, 1, 1, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, MINIMUM_BUTTON_WIDTH, MINIMUM_BUTTON_HEIGHT, null);
		
		UIButton buttonCancel = factory.createButton(parent);
		buttonCancel.setText(TuxGuitar.getProperty("cancel"));
		buttonCancel.addSelectionListener(new UISelectionListener() {
			public void onSelect(UISelectionEvent event) {
				TGTrackTuningDialog.this.dialog.dispose();
			}
		});
		parentLayout.set(buttonCancel, 1, 2, UITableLayout.ALIGN_FILL, UITableLayout.ALIGN_FILL, true, true, 1, 1, MINIMUM_BUTTON_WIDTH, MINIMUM_BUTTON_HEIGHT, null);
		parentLayout.set(buttonCancel, UITableLayout.MARGIN_RIGHT, 0f);
	}
	
	private void onSelectGroup(UIDropDownSelect<TuningGroup> select) {
		this.updatePresetsPanel(select.getSelectedValue(), null);
	}
	
	private void onSelectPreset(UIDropDownSelect<TuningPreset> select) {
		this.currentSelectedPreset = select.getSelectedValue();
		updateTuningTable(select.getSelectedValue());
		updatePresetsButtons();
	}
	
	private void onSavePreset() {
		TuningGroup customTuningsGroup = allTuningsGroup.getGroups().get(0);
		TuningPreset preset = convertTuning(customTuningsGroup, currentSelectedPreset.getName(), this.tuning);
		customTuningsGroup.removeTuningPreset(currentSelectedPreset);
		customTuningsGroup.addTuningPreset(preset);
		TuxGuitar.getInstance().getTuningManager().saveCustomTunings(customTuningsGroup);
		this.updatePresetsPanel(null, preset);
	}
	
	private void onSavePresetAs() {
		TuningGroup customTuningsGroup = allTuningsGroup.getGroups().get(0);
		TuningPreset preset = convertTuning(customTuningsGroup, this.newPresetName.getText(), this.tuning);
		customTuningsGroup.addTuningPreset(preset);
		TuxGuitar.getInstance().getTuningManager().saveCustomTunings(customTuningsGroup);
		this.updatePresetsPanel(null, preset);
	}
	
	private void onDeletePreset() {
		TuningGroup customTuningsGroup = allTuningsGroup.getGroups().get(0);
		customTuningsGroup.removeTuningPreset(currentSelectedPreset);
		TuxGuitar.getInstance().getTuningManager().saveCustomTunings(customTuningsGroup);
		this.currentSelectedPreset = null;
		this.updatePresetsPanel(null, null);
	}
	
	private void updateTuningTable(TuningPreset preset) {
		tuning.clear();
		if (preset!=null && preset.getValues().length>0) {
			for (int value:preset.getValues()) {
				TGTrackTuningModel model = new TGTrackTuningModel();
				model.setValue(value);
				tuning.add(model);
			}
			updateTuningTable();
		}
	}
	
	private void onAddTuningModel() {
		new TGTrackTuningChooserDialog(this).select(new TGTrackTuningChooserHandler() {
			public void handleSelection(TGTrackTuningModel model) {
				addTuningModel(model);
			}
		});
	}
	
	private void onEditTuningModel() {
		final TGTrackTuningModel editingModel = this.tuningTable.getSelectedValue();
		if( editingModel != null ) {
			new TGTrackTuningChooserDialog(this).select(new TGTrackTuningChooserHandler() {
				public void handleSelection(TGTrackTuningModel model) {
					editingModel.setValue(model.getValue());
					updateTuningControls();
				}
			}, editingModel);
		}
	}
	
	private void onRemoveTuningModel() {
		TGTrackTuningModel model = this.tuningTable.getSelectedValue();
		if( model != null ) {
			removeTuningModel(model);
		}
	}
	
	private List<TuningGroup> findParentGroups(TuningGroup group) {
		if (group == null) {
			return(null);
		}
		List<TuningGroup> listGroups = new ArrayList<TuningGroup>();
		do {
			listGroups.add(0,group);
			group = group.getParent();
		} while (group != null);
		return(listGroups);
	}
	
	private List<TuningGroup> findParentGroups(TuningPreset preset) {
		return(findParentGroups(preset.getParent()));
	}
	
	private void moveString(int delta) {
		final TGTrackTuningModel model = this.tuningTable.getSelectedValue();
		if (model != null) {
			int index = this.tuning.indexOf(model);
			this.tuning.remove(index);
			this.tuning.add(index + delta, model);
			this.updateTuningControls();
		}
	}
	
	private boolean isCustomTuningPreset(TuningPreset preset) {
		if (preset == null) {
			return(false);
		}
		TuningGroup parent = preset.getParent();
		while (parent!=null) {
			if (parent.equals(allTuningsGroup.getGroups().get(0))) {
				return(true);
			}
			parent = parent.getParent();
		}
		return(false);
	}
	private TuningPreset convertTuning(TuningGroup parent, String name, List<TGTrackTuningModel> tuningModel) {
		int[] noteValues = new int[tuningModel.size()];
		for (int i=0; i<tuningModel.size();i++) {
			noteValues[i] = tuningModel.get(i).getValue();
		}
		return new TuningPreset(parent, name, noteValues);
	}

	private boolean areTuningsEqual(List<TGTrackTuningModel> tuningModel, TuningPreset tuningPreset) {
		if (tuningModel==null && tuningPreset==null) {
			return(true);
		}
		if (tuningModel!=null && tuningPreset==null) {
			return(false);
		}
		if (tuningModel==null && tuningPreset!=null) {
			return(false);
		}
		if (tuningPreset.getValues().length != tuningModel.size()) {
			return(false);
		}
		for (int i=0; i<tuningModel.size(); i++) {
			if (tuningPreset.getValues()[i] != tuningModel.get(i).getValue()) {
				return(false);
			}
		}
		return(true);
	}
	
	private static boolean areTuningsEqual(List<TGTrackTuningModel> a, List<TGTrackTuningModel> b) {
		if(a.size() == b.size()) {
			for(int i = 0 ; i < a.size(); i ++) {
				if(!a.get(i).getValue().equals(b.get(i).getValue())) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	private void updateTuningTable() {
		TGTrackTuningModel selection = this.tuningTable.getSelectedValue();
		
		this.tuningTable.removeItems();
		for(TGTrackTuningModel model : this.tuning) {
			UITableItem<TGTrackTuningModel> item = new UITableItem<TGTrackTuningModel>(model);
			item.setText(0, this.getValueLabel(model.getValue()));
			item.setText(1, this.getValueLabel(model.getValue(), true));
			
			this.tuningTable.addItem(item);
		}
		
		if( selection != null ) {
			this.tuningTable.setSelectedValue(selection);
		}
	}
	
	private void updateTuningButtons() {
		TGTrackTuningModel model = this.tuningTable.getSelectedValue();
		int index = model != null ? this.tuning.indexOf(model) : -1;
		buttonEdit.setEnabled(model != null);
		buttonDelete.setEnabled(model != null);
		buttonMoveUp.setEnabled(model != null && index > 0);
		buttonMoveDown.setEnabled(model != null && index < this.tuning.size() - 1);
		
		boolean isDefault = areTuningsEqual(this.tuning, this.initialTuning);
		stringTransposition.setEnabled(!isDefault);
		stringTranspositionApplyToChords.setEnabled(!isDefault);
		stringTranspositionTryKeepString.setEnabled(!isDefault);
	}
	
	private void updateTuningControls() {
		this.updateTuningTable();
		this.updatePresetsPanel(null, findTuningInGroup(this.tuning, this.allTuningsGroup));
		this.updateTuningButtons();
	}
	
	private static List<TGTrackTuningModel> getTuningFromTrack(TGTrack track) {
		List<TGTrackTuningModel> tuning = new ArrayList<>();
		for(int i = 0; i < track.stringCount(); i ++) {
			TGString string = track.getString(i + 1);
			TGTrackTuningModel model = new TGTrackTuningModel();
			model.setValue(string.getValue());
			tuning.add(model);
		}
		return tuning;
	}
	
	private void addTuningModel(TGTrackTuningModel model) {
		if( this.tuning.add(model)) {
			this.updateTuningControls();
		}
	}
	
	private void removeTuningModel(TGTrackTuningModel model) {
		if( this.tuning.remove(model)) {
			this.updateTuningControls();
		}
	}
	
	private boolean updateTrackTuning() {
		final TGSongManager songManager = this.findSongManager();
		final TGSong song = this.findSong();
		final TGTrack track = this.findTrack();
		
		final List<TGString> strings = new ArrayList<TGString>();
		for(int i = 0; i < this.tuning.size(); i ++) {
			strings.add(TGSongManager.newString(findSongManager().getFactory(),(i + 1), this.tuning.get(i).getValue()));
		}
		
		final Integer offset = ((songManager.isPercussionChannel(song, track.getChannelId())) ? 0 : this.offsetSpinner.getValue());
		final boolean offsetChanges = (offset != null && !offset.equals(track.getOffset()));
		final boolean tuningChanges = hasTuningChanges(track, strings);
		final boolean transposeStrings = shouldTransposeStrings(track, track.getChannelId());
		final boolean transposeApplyToChords = (transposeStrings && this.stringTranspositionApplyToChords.isSelected());
		final boolean transposeTryKeepString = (transposeStrings && this.stringTranspositionTryKeepString.isSelected());
		
		if( this.validateTrackTuning(strings)) {
			if( tuningChanges || offsetChanges ){
				TGActionProcessor tgActionProcessor = new TGActionProcessor(this.context.getContext(), TGChangeTrackTuningAction.NAME);
				tgActionProcessor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, song);
				tgActionProcessor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track);
				
				if( tuningChanges ) {
					tgActionProcessor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_STRINGS, strings);
					tgActionProcessor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_TRANSPOSE_STRINGS, transposeStrings);
					tgActionProcessor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_TRANSPOSE_TRY_KEEP_STRINGS, transposeTryKeepString);
					tgActionProcessor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_TRANSPOSE_APPLY_TO_CHORDS, transposeApplyToChords);
				}
				if( offsetChanges ) {
					tgActionProcessor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_OFFSET, offset);
				}
				tgActionProcessor.process();
			}
			return true;
		}
		return false;
	}
	
	private boolean validateTrackTuning(List<TGString> strings) {
		if( strings.size() < TGTrack.MIN_STRINGS || strings.size() > TGTrack.MAX_STRINGS ) {
			TGMessageDialogUtil.errorMessage(this.getContext().getContext(), this.dialog, TuxGuitar.getProperty("tuning.strings.range-error", new String[] {Integer.toString(TGTrack.MIN_STRINGS), Integer.toString(TGTrack.MAX_STRINGS)}));
			
			return false;
		}
		return true;
	}
	
	private boolean shouldTransposeStrings(TGTrack track, int selectedChannelId){
		if( this.stringTransposition.isSelected()){
			boolean percussionChannelNew = findSongManager().isPercussionChannel(track.getSong(), selectedChannelId);
			boolean percussionChannelOld = findSongManager().isPercussionChannel(track.getSong(), track.getChannelId());
			
			return (!percussionChannelNew && !percussionChannelOld);
		}
		return false;
	}
	
	private boolean hasTuningChanges(TGTrack track, List<TGString> newStrings){
		List<TGString> oldStrings = track.getStrings();
		//check the number of strings
		if(oldStrings.size() != newStrings.size()){
			return true;
		}
		//check the tuning of strings
		for(int i = 0;i < oldStrings.size();i++){
			TGString oldString = (TGString)oldStrings.get(i);
			boolean stringExists = false;
			for(int j = 0;j < newStrings.size();j++){
				TGString newString = (TGString)newStrings.get(j);
				if(newString.isEqual(oldString)){
					stringExists = true;
				}
			}
			if(!stringExists){
				return true;
			}
		}
		return false;
	}
	
	public String[] getValueLabels() {
		String[] valueNames = new String[MAX_NOTES * MAX_OCTAVES];
		for (int i = 0; i < valueNames.length; i++) {
			valueNames[i] = this.getValueLabel(i, true);
		}
		return valueNames;
	}
	
	public String getValueLabel(Integer value) {
		return this.getValueLabel(value, false);
	}
	
	private String getValueLabel(Integer value, boolean octave) {
		StringBuilder sb = new StringBuilder();
		if( value != null ) {
			sb.append(NOTE_NAMES[value % NOTE_NAMES.length]);
			
			if( octave ) {
				sb.append(value / MAX_NOTES);
			}
		}
		return sb.toString();
	}
	
	private TGSongManager findSongManager() {
		return this.context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER);
	}
	
	private TGSong findSong() {
		return this.context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG);
	}
	
	private TGTrack findTrack() {
		return this.context.getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK);
	}
	
	public TGViewContext getContext() {
		return this.context;
	}
	
	public UIFactory getUIFactory() {
		return TGApplication.getInstance(this.context.getContext()).getFactory();
	}
	
	public UIWindow getDialog() {
		return this.dialog;
	}
}
