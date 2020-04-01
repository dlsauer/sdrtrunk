/*
 *
 *  * ******************************************************************************
 *  * Copyright (C) 2014-2020 Dennis Sheirer
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *  * *****************************************************************************
 *
 *
 */

package io.github.dsheirer.gui.playlist.alias;

import com.google.common.eventbus.Subscribe;
import impl.org.controlsfx.autocompletion.AutoCompletionTextFieldBinding;
import impl.org.controlsfx.autocompletion.SuggestionProvider;
import io.github.dsheirer.alias.Alias;
import io.github.dsheirer.alias.id.AliasID;
import io.github.dsheirer.alias.id.AliasIDType;
import io.github.dsheirer.alias.id.broadcast.BroadcastChannel;
import io.github.dsheirer.alias.id.talkgroup.Talkgroup;
import io.github.dsheirer.alias.id.talkgroup.TalkgroupFormatter;
import io.github.dsheirer.eventbus.MyEventBus;
import io.github.dsheirer.gui.playlist.Editor;
import io.github.dsheirer.gui.playlist.alias.identifier.EmptyIdentifierEditor;
import io.github.dsheirer.gui.playlist.alias.identifier.IdentifierEditorFactory;
import io.github.dsheirer.icon.Icon;
import io.github.dsheirer.playlist.PlaylistManager;
import io.github.dsheirer.preference.PreferenceType;
import io.github.dsheirer.preference.UserPreferences;
import io.github.dsheirer.preference.identifier.IntegerFormat;
import io.github.dsheirer.protocol.Protocol;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import jiconfont.icons.font_awesome.FontAwesome;
import jiconfont.javafx.IconFontFX;
import jiconfont.javafx.IconNode;
import org.controlsfx.control.ToggleSwitch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Editor for configuring aliases
 */
public class AliasConfigurationEditor extends Editor<Alias>
{
    private static final Logger mLog = LoggerFactory.getLogger(AliasConfigurationEditor.class);

    private PlaylistManager mPlaylistManager;
    private UserPreferences mUserPreferences;
    private EditorModificationListener mEditorModificationListener = new EditorModificationListener();
    private TextField mAliasListNameField;
    private TextField mGroupField;
    private TextField mNameField;
    private GridPane mTextFieldPane;
    private Button mSaveButton;
    private Button mResetButton;
    private VBox mButtonBox;
    private ToggleSwitch mMonitorAudioToggleSwitch;
    private ComboBox<Integer> mMonitorPriorityComboBox;
    private ToggleSwitch mRecordAudioToggleSwitch;
    private ColorPicker mColorPicker;
    private ComboBox<Icon> mIconNodeComboBox;
    private SuggestionProvider<String> mListSuggestionProvider;
    private SuggestionProvider<String> mGroupSuggestionProvider;
    private VBox mTitledPanesBox;
    private TitledPane mIdentifierPane;
    private TitledPane mStreamPane;
    private TitledPane mActionPane;
    private ListView<String> mAvailableStreamsView;
    private ListView<BroadcastChannel> mSelectedStreamsView;
    private ListView<AliasID> mIdentifiersList;
    private Button mAddStreamButton;
    private Button mRemoveStreamButton;
    private MenuButton mAddIdentifierButton;
    private Button mDeleteIdentifierButton;
    private Map<AliasIDType,Editor<AliasID>> mIdentifierEditorMap = new HashMap<>();
    private EmptyIdentifierEditor mEmptyIdentifierEditor = new EmptyIdentifierEditor();
    private Editor<AliasID> mIdentifierEditor;
    private VBox mIdentifierEditorBox;

    public AliasConfigurationEditor(PlaylistManager playlistManager, UserPreferences userPreferences)
    {
        mPlaylistManager = playlistManager;
        mUserPreferences = userPreferences;
        MyEventBus.getEventBus().register(this);

        IconFontFX.register(jiconfont.icons.font_awesome.FontAwesome.getIconFont());

        setMaxWidth(Double.MAX_VALUE);

        HBox hbox = new HBox();
        hbox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(getTextFieldPane(), Priority.ALWAYS);
        HBox.setHgrow(getButtonBox(), Priority.NEVER);
        hbox.getChildren().addAll(getTextFieldPane(), getButtonBox());

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(getTitledPanesBox());

        getChildren().addAll(hbox, scrollPane);
    }


    @Subscribe
    public void preferenceUpdated(PreferenceType preferenceType)
    {
        mLog.debug("Preference updated: " + preferenceType);
        if(preferenceType == PreferenceType.TALKGROUP_FORMAT)
        {
            //When the talkgroup format changes, refresh any alias identifiers in the identifier list so that the
            //display string is correctly formatted.
            for(AliasID id: mIdentifiersList.getItems())
            {
                id.valueProperty().setValue(null);
                id.updateValueProperty();
            }
        }

        //Re-set the selected identifier to force it to update controls with the preference change.
        AliasID selected = getIdentifiersList().getSelectionModel().getSelectedItem();

        if(selected != null)
        {
            getIdentifierEditor().setItem(selected);
        }
    }

    @Override
    public void setItem(Alias alias)
    {
        super.setItem(alias);

        refreshAutoCompleteBindings();

        boolean disable = (alias == null);
        getAliasListNameField().setDisable(disable);
        getGroupField().setDisable(disable);
        getNameField().setDisable(disable);
        getRecordAudioToggleSwitch().setDisable(disable);
        getColorPicker().setDisable(disable);
        getMonitorAudioToggleSwitch().setDisable(disable);
        getIconNodeComboBox().setDisable(disable);
        getIdentifiersList().setDisable(disable);
        getIdentifiersList().getItems().clear();
        getAddIdentifierButton().setDisable(disable);

        updateStreamViews();

        if(alias != null)
        {
            getAliasListNameField().setText(alias.getAliasListName());
            getGroupField().setText(alias.getGroup());
            getNameField().setText(alias.getName());
            getRecordAudioToggleSwitch().setSelected(alias.isRecordable());

            Icon icon = null;
            String iconName = alias.getIconName();
            if(iconName != null)
            {
                icon = mPlaylistManager.getIconManager().getModel().getIcon(iconName);
            }
            getIconNodeComboBox().getSelectionModel().select(icon);

            int monitorPriority = alias.getPlaybackPriority();

            boolean canMonitor = (monitorPriority != io.github.dsheirer.alias.id.priority.Priority.DO_NOT_MONITOR);
            getMonitorAudioToggleSwitch().setSelected(canMonitor);

            if(canMonitor && monitorPriority != io.github.dsheirer.alias.id.priority.Priority.DEFAULT_PRIORITY)
            {
                getMonitorPriorityComboBox().getSelectionModel().select(monitorPriority);
            }
            else
            {
                getMonitorPriorityComboBox().getSelectionModel().select(null);
            }

            Color color = ColorUtil.fromInteger(alias.getColor());
            getColorPicker().setValue(color);

            //Only add non-audio identifiers to the list -- audio identifiers are managed separately
            for(AliasID aliasID: alias.getAliasIdentifiers())
            {
                if(!aliasID.isAudioIdentifier())
                {
                    getIdentifiersList().getItems().add(aliasID);
                }
            }
        }
        else
        {
            getAliasListNameField().setText(null);
            getGroupField().setText(null);
            getNameField().setText(null);
            getRecordAudioToggleSwitch().setSelected(false);
            getColorPicker().setValue(Color.BLACK);
            getMonitorPriorityComboBox().getSelectionModel().select(null);
            getMonitorAudioToggleSwitch().setSelected(false);
        }

        modifiedProperty().set(false);
    }

    @Override
    public void save()
    {
        if(modifiedProperty().get())
        {
            Alias alias = getItem();

            if(alias != null)
            {
                alias.setAliasListName(getAliasListNameField().getText());
                alias.setGroup(getGroupField().getText());
                alias.setName(getNameField().getText());
                alias.setRecordable(getRecordAudioToggleSwitch().isSelected());
                alias.setColor(ColorUtil.toInteger(getColorPicker().getValue()));

                boolean canMonitor = getMonitorAudioToggleSwitch().isSelected();
                Integer priority = getMonitorPriorityComboBox().getSelectionModel().getSelectedItem();

                Icon icon = getIconNodeComboBox().getSelectionModel().getSelectedItem();
                alias.setIconName(icon != null ? icon.getName() : null);

                if(canMonitor)
                {
                    if(priority == null)
                    {
                        priority = io.github.dsheirer.alias.id.priority.Priority.DEFAULT_PRIORITY;
                    }

                    alias.setCallPriority(priority);
                }
                else
                {
                    alias.setCallPriority(io.github.dsheirer.alias.id.priority.Priority.DO_NOT_MONITOR);
                }

                //Store broadcast streaming audio channels
                alias.removeAllBroadcastChannels();
                for(BroadcastChannel selected: getSelectedStreamsView().getItems())
                {
                    alias.addAliasID(selected);
                }

                alias.removeNonAudioIdentifiers();
                for(AliasID aliasID: getIdentifiersList().getItems())
                {
                    alias.addAliasID(aliasID);
                }
            }

            modifiedProperty().set(false);
        }
    }

    @Override
    public void dispose()
    {
    }

    private VBox getTitledPanesBox()
    {
        if(mTitledPanesBox == null)
        {
            mTitledPanesBox = new VBox();
            mTitledPanesBox.setMaxWidth(Double.MAX_VALUE);
            mTitledPanesBox.getChildren().addAll(getIdentifierPane(), getStreamPane(), getActionPane());
        }

        return mTitledPanesBox;
    }

    private TitledPane getIdentifierPane()
    {
        if(mIdentifierPane == null)
        {
            mIdentifierPane = new TitledPane("Identifiers", getIdentifierEditorBox());
        }

        return mIdentifierPane;
    }

    private VBox getIdentifierEditorBox()
    {
        if(mIdentifierEditorBox == null)
        {
            VBox buttonsBox = new VBox();
            buttonsBox.setSpacing(10);
            buttonsBox.getChildren().addAll(getAddIdentifierButton(), getDeleteIdentifierButton());

            HBox hbox = new HBox();
            hbox.setSpacing(10);
            HBox.setHgrow(getIdentifiersList(), Priority.ALWAYS);
            hbox.getChildren().addAll(getIdentifiersList(), buttonsBox);

            mIdentifierEditorBox = new VBox();
            mIdentifierEditorBox.setSpacing(10);
            mIdentifierEditorBox.getChildren().addAll(hbox, getIdentifierEditor());
        }

        return mIdentifierEditorBox;
    }

    private Editor<AliasID> getIdentifierEditor()
    {
        if(mIdentifierEditor == null)
        {
            mIdentifierEditor = mEmptyIdentifierEditor;
        }

        return mIdentifierEditor;
    }

    /**
     * Updates the editor and loads the alias id for editing/viewing
     * @param aliasID
     */
    private void setIdentifier(AliasID aliasID)
    {
        Editor<AliasID> editor = null;

        if(aliasID != null)
        {
            editor = mIdentifierEditorMap.get(aliasID.getType());

            if(editor == null)
            {
                editor = IdentifierEditorFactory.getEditor(aliasID.getType(), mUserPreferences);
                mIdentifierEditorMap.put(aliasID.getType(), editor);
            }
        }

        if(editor == null)
        {
            editor = mEmptyIdentifierEditor;
        }

        if(mIdentifierEditor != editor)
        {
            getIdentifierEditorBox().getChildren().remove(mIdentifierEditor);
            mIdentifierEditor = editor;
            getIdentifierEditorBox().getChildren().add(mIdentifierEditor);
        }

        mIdentifierEditor.setItem(aliasID);
    }

    private ListView<AliasID> getIdentifiersList()
    {
        if(mIdentifiersList == null)
        {
            mIdentifiersList = new ListView<>(FXCollections.observableArrayList(AliasID.extractor()));
            mIdentifiersList.setPrefHeight(75);
            mIdentifiersList.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> setIdentifier(newValue));
            mIdentifiersList.setCellFactory(new Callback<ListView<AliasID>,ListCell<AliasID>>()
            {
                @Override
                public ListCell<AliasID> call(ListView<AliasID> param)
                {
                    return new AliasIdentifierCell();
                }
            });
        }

        return mIdentifiersList;
    }

    private MenuButton getAddIdentifierButton()
    {
        if(mAddIdentifierButton == null)
        {
            mAddIdentifierButton = new MenuButton("Add");
            mAddIdentifierButton.setMaxWidth(Double.MAX_VALUE);
            mAddIdentifierButton.setDisable(true);

            Menu talkgroup = new Menu("Talkgroup");
            MenuItem apco25Item = new MenuItem("APCO-25");
            MenuItem ltrItem = new MenuItem("LTR");
            MenuItem mdc1200Item = new MenuItem("MDC-1200");
            MenuItem mpt1327Item = new MenuItem("MPT-1327");
            MenuItem passportItem = new MenuItem("Passport");
            talkgroup.getItems().addAll(apco25Item, ltrItem, mdc1200Item, mpt1327Item, passportItem);

            Menu talkgroupRange = new Menu("Talkgroup Range");
            Menu radioId = new Menu("Radio ID");
            Menu radioIdRange = new Menu("Radio ID Range");

            mAddIdentifierButton.getItems().addAll(talkgroup, talkgroupRange, radioId, radioIdRange);
        }

        return mAddIdentifierButton;
    }

    private Button getDeleteIdentifierButton()
    {
        if(mDeleteIdentifierButton == null)
        {
            mDeleteIdentifierButton = new Button("Delete");
            mDeleteIdentifierButton.setMaxWidth(Double.MAX_VALUE);
            mDeleteIdentifierButton.setDisable(true);
        }

        return mDeleteIdentifierButton;
    }

    private TitledPane getStreamPane()
    {
        if(mStreamPane == null)
        {
            VBox buttonBox = new VBox();
            buttonBox.setMaxHeight(Double.MAX_VALUE);
            buttonBox.setAlignment(Pos.CENTER);
            buttonBox.setSpacing(5);
            buttonBox.getChildren().addAll(new Label(" "), getAddStreamButton(), getRemoveStreamButton());

            VBox availableBox = new VBox();
            VBox.setVgrow(getAvailableStreamsView(), Priority.ALWAYS);
            availableBox.getChildren().addAll(new Label("Available"), getAvailableStreamsView());

            VBox selectedBox = new VBox();
            VBox.setVgrow(getSelectedStreamsView(), Priority.ALWAYS);
            selectedBox.getChildren().addAll(new Label("Selected"),getSelectedStreamsView());

            HBox hbox = new HBox();
            hbox.setSpacing(10);
            HBox.setHgrow(availableBox, Priority.ALWAYS);
            HBox.setHgrow(selectedBox, Priority.ALWAYS);
            hbox.getChildren().addAll(availableBox, buttonBox, selectedBox);

            mStreamPane = new TitledPane("Streaming", hbox);
            mStreamPane.setExpanded(false);
        }

        return mStreamPane;
    }

    private void updateStreamViews()
    {
        getAvailableStreamsView().getItems().clear();
        getSelectedStreamsView().getItems().clear();

        if(getItem() != null)
        {
            List<String> availableStreams = mPlaylistManager.getBroadcastModel().getBroadcastConfigurationNames();

            Set<BroadcastChannel> selectedChannels = getItem().getBroadcastChannels();

            for(BroadcastChannel channel: selectedChannels)
            {
                if(availableStreams.contains(channel.getChannelName()))
                {
                    availableStreams.remove(channel.getChannelName());
                }
            }

            getSelectedStreamsView().getItems().addAll(selectedChannels);
            getAvailableStreamsView().getItems().addAll(availableStreams);
        }
    }

    private ListView<String> getAvailableStreamsView()
    {
        if(mAvailableStreamsView == null)
        {
            mAvailableStreamsView = new ListView<>();
            mAvailableStreamsView.setPrefHeight(50);
        }

        return mAvailableStreamsView;
    }

    private ListView<BroadcastChannel> getSelectedStreamsView()
    {
        if(mSelectedStreamsView == null)
        {
            mSelectedStreamsView = new ListView<>();
            mSelectedStreamsView.setPrefHeight(50);
            mSelectedStreamsView.getItems().addListener((ListChangeListener<BroadcastChannel>)c -> {
                String title = "Streaming";

                if(getSelectedStreamsView().getItems().size() > 0)
                {
                    title += " (" + getSelectedStreamsView().getItems().size() + ")";
                }

                getStreamPane().setText(title);
            });
        }

        return mSelectedStreamsView;
    }

    private Button getAddStreamButton()
    {
        if(mAddStreamButton == null)
        {
            mAddStreamButton = new Button();
            mAddStreamButton.disableProperty().bind(Bindings.isEmpty(getAvailableStreamsView().getItems())
                    .or(Bindings.isNull(getAvailableStreamsView().getSelectionModel().selectedItemProperty())));
            mAddStreamButton.setMaxWidth(Double.MAX_VALUE);
            mAddStreamButton.setGraphic(new IconNode(FontAwesome.ANGLE_RIGHT));
            mAddStreamButton.setAlignment(Pos.CENTER);
            mAddStreamButton.setOnAction(event -> {
                String stream = getAvailableStreamsView().getSelectionModel().getSelectedItem();

                if(stream != null)
                {
                    getAvailableStreamsView().getItems().remove(stream);
                    getSelectedStreamsView().getItems().add(new BroadcastChannel(stream));
                    modifiedProperty().set(true);
                }
            });

        }

        return mAddStreamButton;
    }

    private Button getRemoveStreamButton()
    {
        if(mRemoveStreamButton == null)
        {
            mRemoveStreamButton = new Button();
            mRemoveStreamButton.disableProperty().bind(Bindings.isEmpty(getSelectedStreamsView().getItems())
                    .or(Bindings.isNull(getSelectedStreamsView().getSelectionModel().selectedItemProperty())));
            mRemoveStreamButton.setMaxWidth(Double.MAX_VALUE);
            mRemoveStreamButton.setGraphic(new IconNode(FontAwesome.ANGLE_LEFT));
            mRemoveStreamButton.setAlignment(Pos.CENTER);
            mRemoveStreamButton.setOnAction(event -> {
                BroadcastChannel broadcastChannel = getSelectedStreamsView().getSelectionModel().getSelectedItem();

                if(broadcastChannel != null)
                {
                    getSelectedStreamsView().getItems().remove(broadcastChannel);
                    getAvailableStreamsView().getItems().add(broadcastChannel.getChannelName());
                    modifiedProperty().set(true);
                }
            });
        }

        return mRemoveStreamButton;
    }

    private TitledPane getActionPane()
    {
        if(mActionPane == null)
        {
            HBox hbox = new HBox();
            mActionPane = new TitledPane("Actions", hbox);
            mActionPane.setExpanded(false);
        }

        return mActionPane;
    }

    private GridPane getTextFieldPane()
    {
        if(mTextFieldPane == null)
        {
            mTextFieldPane = new GridPane();
            mTextFieldPane.setPadding(new Insets(10, 5, 10,10));
            mTextFieldPane.setVgap(10);
            mTextFieldPane.setHgap(10);

            Label aliasListLabel = new Label("Alias List");
            GridPane.setHalignment(aliasListLabel, HPos.RIGHT);
            GridPane.setConstraints(aliasListLabel, 0, 0);
            mTextFieldPane.getChildren().add(aliasListLabel);

            GridPane.setConstraints(getAliasListNameField(), 1, 0);
            GridPane.setHgrow(getAliasListNameField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getAliasListNameField());

            Label recordAudioLabel = new Label("Record Audio");
            GridPane.setHalignment(recordAudioLabel, HPos.RIGHT);
            GridPane.setConstraints(recordAudioLabel, 2, 0);
            mTextFieldPane.getChildren().add(recordAudioLabel);

            GridPane.setConstraints(getRecordAudioToggleSwitch(), 3, 0);
            mTextFieldPane.getChildren().add(getRecordAudioToggleSwitch());

            Label groupLabel = new Label("Group");
            GridPane.setHalignment(groupLabel, HPos.RIGHT);
            GridPane.setConstraints(groupLabel, 0, 1);
            mTextFieldPane.getChildren().add(groupLabel);

            GridPane.setConstraints(getGroupField(), 1, 1);
            GridPane.setHgrow(getGroupField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getGroupField());

            Label monitorAudioLabel = new Label("Monitor Audio");
            GridPane.setHalignment(monitorAudioLabel, HPos.RIGHT);
            GridPane.setConstraints(monitorAudioLabel, 2, 1);
            mTextFieldPane.getChildren().add(monitorAudioLabel);

            GridPane.setConstraints(getMonitorAudioToggleSwitch(), 3, 1);
            mTextFieldPane.getChildren().add(getMonitorAudioToggleSwitch());

            Label colorLabel = new Label("Color");
            GridPane.setHalignment(colorLabel, HPos.RIGHT);
            GridPane.setConstraints(colorLabel, 4, 1);
            mTextFieldPane.getChildren().add(colorLabel);

            GridPane.setConstraints(getColorPicker(), 5, 1);
            mTextFieldPane.getChildren().add(getColorPicker());


            Label nameLabel = new Label("Alias");
            GridPane.setHalignment(nameLabel, HPos.RIGHT);
            GridPane.setConstraints(nameLabel, 0, 2);
            mTextFieldPane.getChildren().add(nameLabel);

            GridPane.setConstraints(getNameField(), 1, 2);
            GridPane.setHgrow(getNameField(), Priority.ALWAYS);
            mTextFieldPane.getChildren().add(getNameField());

            Label monitorPriorityLabel = new Label("Monitor Priority");
            GridPane.setHalignment(monitorPriorityLabel, HPos.RIGHT);
            GridPane.setConstraints(monitorPriorityLabel, 2, 2);
            mTextFieldPane.getChildren().add(monitorPriorityLabel);

            GridPane.setConstraints(getMonitorPriorityComboBox(), 3, 2);
            mTextFieldPane.getChildren().add(getMonitorPriorityComboBox());

            Label iconLabel = new Label("Icon");
            GridPane.setHalignment(iconLabel, HPos.RIGHT);
            GridPane.setConstraints(iconLabel, 4, 2);
            mTextFieldPane.getChildren().add(iconLabel);

            GridPane.setConstraints(getIconNodeComboBox(), 5, 2);
            mTextFieldPane.getChildren().add(getIconNodeComboBox());
        }

        return mTextFieldPane;
    }

    private ToggleSwitch getMonitorAudioToggleSwitch()
    {
        if(mMonitorAudioToggleSwitch == null)
        {
            mMonitorAudioToggleSwitch = new ToggleSwitch();
            mMonitorAudioToggleSwitch.setDisable(true);
            mMonitorAudioToggleSwitch.selectedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mMonitorAudioToggleSwitch;
    }

    private ComboBox<Integer> getMonitorPriorityComboBox()
    {
        if(mMonitorPriorityComboBox == null)
        {
            mMonitorPriorityComboBox = new ComboBox<>();
            mMonitorPriorityComboBox.getItems().add(null);
            for(int x = io.github.dsheirer.alias.id.priority.Priority.MIN_PRIORITY;
                    x < io.github.dsheirer.alias.id.priority.Priority.MAX_PRIORITY; x++)
            {
                mMonitorPriorityComboBox.getItems().add(x);
            }

            mMonitorPriorityComboBox.disableProperty().bind(getMonitorAudioToggleSwitch().selectedProperty().not());
            mMonitorPriorityComboBox.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mMonitorPriorityComboBox;
    }

    private ToggleSwitch getRecordAudioToggleSwitch()
    {
        if(mRecordAudioToggleSwitch == null)
        {
            mRecordAudioToggleSwitch = new ToggleSwitch();
            mRecordAudioToggleSwitch.setDisable(true);
            mRecordAudioToggleSwitch.selectedProperty()
                .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mRecordAudioToggleSwitch;
    }

    private ColorPicker getColorPicker()
    {
        if(mColorPicker == null)
        {
            mColorPicker = new ColorPicker(Color.BLACK);
            mColorPicker.setDisable(true);
            mColorPicker.setEditable(true);
            mColorPicker.setStyle("-fx-color-rect-width: 60px; -fx-color-label-visible: false;");
            mColorPicker.setOnAction(event -> modifiedProperty().set(true));
        }

        return mColorPicker;
    }

    private ComboBox<Icon> getIconNodeComboBox()
    {
        if(mIconNodeComboBox == null)
        {
            mIconNodeComboBox = new ComboBox<>();
            mIconNodeComboBox.setDisable(true);
            mIconNodeComboBox.getItems().addAll(mPlaylistManager.getIconManager().getIcons());
            mIconNodeComboBox.setCellFactory(new IconCellFactory());
            mIconNodeComboBox.getSelectionModel().selectedItemProperty()
                    .addListener((observable, oldValue, newValue) -> modifiedProperty().set(true));
        }

        return mIconNodeComboBox;
    }

    /**
     * Refreshes the system and site text field auto-completion lists.
     */
    private void refreshAutoCompleteBindings()
    {
        getListSuggestionProvider().clearSuggestions();
        getListSuggestionProvider().addPossibleSuggestions(mPlaylistManager.getAliasModel().getListNames());
        getGroupSuggestionProvider().clearSuggestions();
        getGroupSuggestionProvider().addPossibleSuggestions(mPlaylistManager.getAliasModel().getGroupNames());
    }

    protected TextField getAliasListNameField()
    {
        if(mAliasListNameField == null)
        {
            mAliasListNameField = new TextField();
            mAliasListNameField.setDisable(true);
            mAliasListNameField.setMaxWidth(Double.MAX_VALUE);
            mAliasListNameField.textProperty().addListener(mEditorModificationListener);
            new AutoCompletionTextFieldBinding<>(mAliasListNameField, getListSuggestionProvider());
        }

        return mAliasListNameField;
    }

    private SuggestionProvider<String> getListSuggestionProvider()
    {
        if(mListSuggestionProvider == null)
        {
            mListSuggestionProvider = SuggestionProvider.create(mPlaylistManager.getChannelModel().getSystemNames());
        }

        return mListSuggestionProvider;
    }

    private SuggestionProvider<String> getGroupSuggestionProvider()
    {
        if(mGroupSuggestionProvider == null)
        {
            mGroupSuggestionProvider = SuggestionProvider.create(mPlaylistManager.getChannelModel().getSiteNames());
        }

        return mGroupSuggestionProvider;
    }

    protected TextField getGroupField()
    {
        if(mGroupField == null)
        {
            mGroupField = new TextField();
            mGroupField.setDisable(true);
            mGroupField.setMaxWidth(Double.MAX_VALUE);
            mGroupField.textProperty().addListener(mEditorModificationListener);
            new AutoCompletionTextFieldBinding<>(mGroupField, getGroupSuggestionProvider());
        }

        return mGroupField;
    }

    protected TextField getNameField()
    {
        if(mNameField == null)
        {
            mNameField = new TextField();
            mNameField.setDisable(true);
            mNameField.setMaxWidth(Double.MAX_VALUE);
            mNameField.textProperty().addListener(mEditorModificationListener);
        }

        return mNameField;
    }

    private VBox getButtonBox()
    {
        if(mButtonBox == null)
        {
            mButtonBox = new VBox();
            mButtonBox.setSpacing(10);
            mButtonBox.setPadding(new Insets(10, 10, 10, 5));
            mButtonBox.getChildren().addAll(getSaveButton(), getResetButton());
        }

        return mButtonBox;
    }

    private Button getSaveButton()
    {
        if(mSaveButton == null)
        {
            mSaveButton = new Button(" Save ");
            mSaveButton.setTextAlignment(TextAlignment.CENTER);
            mSaveButton.setMaxWidth(Double.MAX_VALUE);
            mSaveButton.disableProperty().bind(modifiedProperty().not());
            mSaveButton.setOnAction(event -> save());

        }

        return mSaveButton;
    }

    private Button getResetButton()
    {
        if(mResetButton == null)
        {
            mResetButton = new Button("Reset");
            mResetButton.setTextAlignment(TextAlignment.CENTER);
            mResetButton.setMaxWidth(Double.MAX_VALUE);
            mResetButton.disableProperty().bind(modifiedProperty().not());
            mResetButton.setOnAction(event -> {
                modifiedProperty().set(false);
                setItem(getItem());
            });
        }

        return mResetButton;
    }

    /**
     * Simple string change listener that sets the editor modified flag to true any time text fields are edited.
     */
    public class EditorModificationListener implements ChangeListener<String>
    {
        @Override
        public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
        {
            modifiedProperty().set(true);
        }
    }

    public class AliasIdentifierCell extends ListCell<AliasID>
    {
        @Override
        protected void updateItem(AliasID item, boolean empty)
        {
            super.updateItem(item, empty);

            if(item != null)
            {
                if(item instanceof Talkgroup)
                {
                    Talkgroup talkgroup = (Talkgroup)item;
                    Protocol protocol = talkgroup.getProtocol();
                    IntegerFormat integerFormat = mUserPreferences.getTalkgroupFormatPreference()
                        .getTalkgroupFormat(protocol);
                    String formatted = TalkgroupFormatter.format(protocol, talkgroup.getValue(), integerFormat);
                    StringBuilder sb = new StringBuilder();
                    sb.append("Talkgroup:").append(formatted);
                    sb.append(" Protocol:").append((talkgroup.getProtocol()));

                    if(!talkgroup.isValid())
                    {
                        sb.append(" **NOT VALID**");
                    }
                    setText(sb.toString());
                }
                else
                {
                    setText(item.toString());
                }
            }
            else
            {
                setText(null);
            }
        }
    }

    /**
     * Cell factory for combo box for dislaying icon name and graphic
     */
    public class IconCellFactory implements Callback<ListView<Icon>, ListCell<Icon>>
    {
        @Override
        public ListCell<Icon> call(ListView<Icon> param)
        {
            ListCell<Icon> cell = new ListCell<>()
            {
                @Override
                protected void updateItem(Icon item, boolean empty)
                {
                    super.updateItem(item, empty);

                    if(empty)
                    {
                        setText(null);
                        setGraphic(null);
                    }
                    else
                    {
                        setText(item.getName());

                        String path = item.getPath();

                        if(path.startsWith("images"))
                        {
                            try
                            {
                                Image image = new Image(path, 0, 20, true, true);
                                setGraphic(new ImageView(image));
                            }
                            catch(Exception e)
                            {

                            }
                        }
                    }
                }
            };

            return cell;
        }
    }
}
