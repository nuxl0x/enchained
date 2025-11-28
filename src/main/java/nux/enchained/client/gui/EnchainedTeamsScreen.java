package nux.enchained.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import nux.enchained.client.skin.EnchainedPlayerSkins;
import nux.enchained.network.EnchainedNetworking;
import nux.enchained.network.EnchainedNetworking.TeamSnapshot;
import nux.enchained.network.EnchainedNetworking.PlayerSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Enchained Teams GUI (admin side + stubbed data)
 *
 * Admin views implemented:
 *  - Admin main: Create Team button + team list (scrollable)
 *  - Create Team view: name + RGB + color preview
 *  - Team Manager: members list, drag-reorder to set leader, lock/unlock, disband, add player
 *  - Add Player view: select player from all known players (scrollable), shows team badge
 *
 * NOTE: This is client-side UI only; all real changes should be sent
 * to the server and persisted via TeamManager.
 */
public class EnchainedTeamsScreen extends Screen {

    // ------------------------
    // View modes
    // ------------------------

    private enum ViewMode {
        PLAYER,
        ADMIN_MAIN,
        ADMIN_CREATE_TEAM,
        ADMIN_TEAM_MANAGER,
        ADMIN_ADD_PLAYER
    }

    private ViewMode currentView = ViewMode.PLAYER;
    private ViewMode previousView = ViewMode.PLAYER;

    // Admin detection
    private boolean isAdmin = false;

    // Tabs (admin only)
    private ButtonWidget tabPlayerButton;
    private ButtonWidget tabAdminButton;

    // Admin main panel
    private ButtonWidget adminCreateTeamButton;

    // Create team view widgets
    private TextFieldWidget teamNameField;
    private TextFieldWidget redField;
    private TextFieldWidget greenField;
    private TextFieldWidget blueField;
    private ButtonWidget createTeamDoneButton;
    private ButtonWidget createTeamCancelButton;

    // Team manager widgets
    private ButtonWidget tmBackButton;
    private ButtonWidget tmLockButton;
    private ButtonWidget tmUnlockButton;
    private ButtonWidget tmDisbandButton;
    private ButtonWidget tmAddPlayerButton;

    // Add player widgets
    private ButtonWidget apBackButton;

    // Team list (admin main, right side)
    private final List<ClientTeam> teams = new ArrayList<>();
    private int teamListScroll = 0;
    private final int teamEntryHeight = 22;

    // Selected team for Team Manager
    private ClientTeam selectedTeam = null;

    // Team Manager member list
    private int memberListScroll = 0;
    private final int memberEntryHeight = 24;
    private int draggingMemberIndex = -1;
    private int draggingMouseYOffset = 0;

    // animation + mouse state
    private double draggingMouseY = 0;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private final Map<UUID, Float> memberAnimY = new HashMap<>();

    // Add Player list
    private final List<ClientPlayerInfo> allPlayers = new ArrayList<>();
    private int addPlayerScroll = 0;
    private final int addPlayerEntryHeight = 22;

    // ----- Player-side state -----

    private ClientPlayerInfo selfInfo = null;

    private enum PlayerSubView {
        MAIN,
        CREATE_TEAM,
        JOIN_TEAM,
        LEADER_REQUESTS,
        TRANSFER_OWNERSHIP
    }

    private PlayerSubView playerSubView = PlayerSubView.MAIN;

    // which teams this client has requested to join (by lowercase name)
    private final Set<String> myPendingJoinTeams = new HashSet<>();

    // player view scrolls
    private int playerMemberListScroll = 0;
    private final int playerMemberEntryHeight = 24;

    private int joinTeamScroll = 0;
    private final int joinTeamEntryHeight = 22;

    private int leaderRequestsScroll = 0;
    private final int leaderRequestsEntryHeight = 22;

    private int transferOwnershipScroll = 0;
    private final int transferOwnershipEntryHeight = 22;

    // player buttons
    private ButtonWidget playerCreateTeamButton;
    private ButtonWidget playerJoinTeamButton;
    private ButtonWidget playerLeaveTeamButton;
    private ButtonWidget playerViewRequestsButton;
    private ButtonWidget playerTransferOwnerButton;

    public EnchainedTeamsScreen() {
        super(Text.literal("Enchained Teams"));
    }

    @Override
    protected void init() {
        super.init();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client != null ? client.player : null;

        // Admin if op-level >= 2
        this.isAdmin = player != null && player.hasPermissionLevel(2);

        if (!isAdmin) {
            currentView = ViewMode.PLAYER;
        } else {
            currentView = ViewMode.ADMIN_MAIN;
        }

        EnchainedNetworking.requestFullSync();

        rebuildView();
    }

    public void applyFullSync(List<TeamSnapshot> teamSnapshots, List<PlayerSnapshot> playerSnapshots) {
        ClientTeam oldSelfTeam = selfInfo != null ? selfInfo.team : null;

        this.teams.clear();
        this.allPlayers.clear();
        this.memberAnimY.clear();

        // Build teams
        Map<String, ClientTeam> teamsByKey = new HashMap<>();
        for (TeamSnapshot snap : teamSnapshots) {
            ClientTeam ct = new ClientTeam(snap.name, snap.color);
            ct.locked = snap.locked;
            this.teams.add(ct);
            teamsByKey.put(snap.name.toLowerCase(Locale.ROOT), ct);
        }

        // Build players
        MinecraftClient mc = MinecraftClient.getInstance();
        var networkHandler = mc != null ? mc.getNetworkHandler() : null;

        Map<UUID, ClientPlayerInfo> playerById = new HashMap<>();
        for (PlayerSnapshot ps : playerSnapshots) {
            GameProfile profile;

            if (networkHandler != null) {
                var entry = networkHandler.getPlayerListEntry(ps.uuid);
                if (entry != null) {
                    profile = entry.getProfile(); // has skin properties
                } else {
                    profile = new GameProfile(ps.uuid, ps.name); // fallback
                }
            } else {
                profile = new GameProfile(ps.uuid, ps.name); // fallback
            }

            ClientPlayerInfo info = new ClientPlayerInfo(ps.uuid, ps.name, profile, null);
            this.allPlayers.add(info);
            playerById.put(ps.uuid, info);
        }

        // Resolve "self" from the snapshot list
        selfInfo = null;
        if (mc != null && mc.player != null) {
            UUID selfId = mc.player.getUuid();
            selfInfo = playerById.get(selfId);
        }

        // Attach members to teams
        for (TeamSnapshot snap : teamSnapshots) {
            ClientTeam ct = teamsByKey.get(snap.name.toLowerCase(Locale.ROOT));
            if (ct == null) continue;

            // members
            for (UUID memberId : snap.members) {
                ClientPlayerInfo info = playerById.get(memberId);
                if (info == null) continue;
                ClientMember cm = new ClientMember(info.uuid, info.name, info.profile);
                ct.members.add(cm);
                info.team = ct;
            }

            // pending join requests (you need to add this to TeamSnapshot)
            if (snap.joinRequests != null) { // <-- add joinRequests: List<UUID> to TeamSnapshot
                for (UUID reqId : snap.joinRequests) {
                    ClientPlayerInfo info = playerById.get(reqId);
                    if (info == null) continue;
                    ClientMember cm = new ClientMember(info.uuid, info.name, info.profile);
                    ct.joinRequests.add(cm);

                    // if this client has a pending request to this team, remember it
                    if (selfInfo != null && selfInfo.uuid.equals(reqId)) {
                        myPendingJoinTeams.add(ct.name.toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        // Preserve selection by name if possible
        if (this.selectedTeam != null) {
            this.selectedTeam = teamsByKey.get(this.selectedTeam.name.toLowerCase(Locale.ROOT));
        }
        if (this.selectedTeam == null && !this.teams.isEmpty()) {
            this.selectedTeam = this.teams.get(0);
        }

        // Reset scroll/drag state
        this.teamListScroll = 0;
        this.memberListScroll = 0;
        this.addPlayerScroll = 0;
        this.draggingMemberIndex = -1;

        if (selfInfo != null && selfInfo.team != null) {
            myPendingJoinTeams.clear();
        }

        ClientTeam newSelfTeam = selfInfo != null ? selfInfo.team : null;

        // If team changed, adjust subview a bit
        if (!Objects.equals(oldSelfTeam, newSelfTeam)) {
            if (newSelfTeam == null) {
                // We’re no longer in a team -> go back to main player view
                playerSubView = PlayerSubView.MAIN;
            }
        }

        // If selectedTeam vanished but we were in its manager view, go back
        if (currentView == ViewMode.ADMIN_TEAM_MANAGER && selectedTeam == null) {
            currentView = ViewMode.ADMIN_MAIN;
        }

        // Rebuild all widgets to match the new data
        rebuildView();
    }

    public void onJoinRequestResult(String teamName, boolean accepted) {
        String key = teamName.toLowerCase(Locale.ROOT);
        myPendingJoinTeams.remove(key);
        if (accepted && selfInfo != null) {
            // the server will update selfInfo.team on next FullSync;
            // nothing else needed here
        }
    }

    // ------------------------
    // Tabs
    // ------------------------

    private void setupTabs() {
        if (!isAdmin) {
            tabPlayerButton = null;
            tabAdminButton = null;
            return;
        }

        int tabWidth = 60;
        int tabHeight = 20;
        int spacing = 4;
        int x = 6;
        int y = 6;

        tabPlayerButton = ButtonWidget.builder(Text.literal("Player"), button -> {
            currentView = ViewMode.PLAYER;
            rebuildView();
        }).dimensions(x, y, tabWidth, tabHeight).build();
        this.addDrawableChild(tabPlayerButton);

        tabAdminButton = ButtonWidget.builder(Text.literal("Admin"), button -> {
            currentView = ViewMode.ADMIN_MAIN;
            rebuildView();
        }).dimensions(x + tabWidth + spacing, y, tabWidth, tabHeight).build();
        this.addDrawableChild(tabAdminButton);

        updateTabStates();
    }

    private void updateTabStates() {
        if (!isAdmin) return;
        if (tabPlayerButton != null) {
            tabPlayerButton.active = (currentView != ViewMode.PLAYER);
        }
        if (tabAdminButton != null) {
            // Only inactive when we're already on admin views
            tabAdminButton.active = (currentView == ViewMode.PLAYER);
        }
    }

    // ------------------------
    // View building
    // ------------------------

    private void rebuildView() {
        // Clear all widgets and re-add everything for this view
        this.clearChildren();

        // Reset any widgets that are lazily created in render()
        playerLeaveTeamButton = null;
        playerViewRequestsButton = null;
        playerTransferOwnerButton = null;
        tmDisbandButton = null;

        // Recreate tabs (if admin)
        setupTabs();

        switch (currentView) {
            case PLAYER -> buildPlayerView();
            case ADMIN_MAIN -> buildAdminMainView();
            case ADMIN_CREATE_TEAM -> buildAdminCreateTeamView();
            case ADMIN_TEAM_MANAGER -> buildAdminTeamManagerView();
            case ADMIN_ADD_PLAYER -> buildAdminAddPlayerView();
            default -> {}
        }
    }

    // ------------------------
    // PLAYER VIEW (placeholder for now)
    // ------------------------

    private void buildPlayerView() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity player = mc != null ? mc.player : null;

        // Try to resolve selfInfo lazily if not yet set
        if (selfInfo == null && player != null) {
            UUID selfId = player.getUuid();
            for (ClientPlayerInfo info : allPlayers) {
                if (info.uuid.equals(selfId)) {
                    selfInfo = info;
                    break;
                }
            }
        }

        // Decide what to build based on current sub-view
        switch (playerSubView) {
            case MAIN -> buildPlayerMainView();
            case CREATE_TEAM -> buildPlayerCreateTeamView();
            case JOIN_TEAM -> buildPlayerJoinTeamView();
            case LEADER_REQUESTS -> buildPlayerLeaderRequestsView();
            case TRANSFER_OWNERSHIP -> buildPlayerTransferOwnershipView();
        }
    }

    private void buildPlayerMainView() {
        // No extra widgets if we don't know who we are
        ClientTeam myTeam = selfInfo != null ? selfInfo.team : null;

        if (myTeam == null) {
            // Not in a team -> show "Create Team" + "Join Team"
            int centerX = this.width / 2;
            int startY = this.height / 2 - 20;
            int buttonWidth = 120;
            int buttonHeight = 20;
            int gap = 6;

            playerCreateTeamButton = ButtonWidget.builder(Text.literal("Create Team"), button -> {
                // same UI as admin create, but from player subview
                playerSubView = PlayerSubView.CREATE_TEAM;
                rebuildView();
            }).dimensions(centerX - buttonWidth / 2, startY, buttonWidth, buttonHeight).build();
            this.addDrawableChild(playerCreateTeamButton);

            playerJoinTeamButton = ButtonWidget.builder(Text.literal("Join Team"), button -> {
                playerSubView = PlayerSubView.JOIN_TEAM;
                rebuildView();
            }).dimensions(centerX - buttonWidth / 2, startY + buttonHeight + gap, buttonWidth, buttonHeight).build();
            this.addDrawableChild(playerJoinTeamButton);
        } else {
            // In a team -> show leave / leader controls in other builders
            // (buttons themselves are added in "team" sub-views during render)
            // nothing to add here; they are added in the sub-builders
        }
    }

    private void buildPlayerCreateTeamView() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int fieldWidth = 180;
        int fieldHeight = 20;
        int gap = 6;

        // Team name field (reuse same fields as admin so onCreateTeamDone works)
        teamNameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Text.literal("Team Name"));
        teamNameField.setPlaceholder(Text.literal("Team name"));
        this.addDrawableChild(teamNameField);

        // RGB fields
        int rgbFieldX = centerX - fieldWidth / 2 + 40;
        int rgbY = startY + fieldHeight + gap;

        redField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("R"));
        redField.setText("255");
        this.addDrawableChild(redField);

        rgbY += fieldHeight + gap;
        greenField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("G"));
        greenField.setText("255");
        this.addDrawableChild(greenField);

        rgbY += fieldHeight + gap;
        blueField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("B"));
        blueField.setText("255");
        this.addDrawableChild(blueField);

        // Done + Cancel
        int buttonY = rgbY + fieldHeight + 12;

        createTeamDoneButton = ButtonWidget.builder(Text.literal("Done"), button -> {
            onCreateTeamDone(); // this already branches admin/player based on isAdmin + currentView
        }).dimensions(centerX - 90, buttonY, 80, 20).build();
        this.addDrawableChild(createTeamDoneButton);

        createTeamCancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> {
            // For players, just go back to the player main view
            playerSubView = PlayerSubView.MAIN;
            currentView = ViewMode.PLAYER;
            rebuildView();
        }).dimensions(centerX + 10, buttonY, 80, 20).build();
        this.addDrawableChild(createTeamCancelButton);
    }

    private void buildPlayerJoinTeamView() {
        int backWidth = 80;
        int backHeight = 20;
        int x = this.width - backWidth - 10;
        int y = 10;

        ButtonWidget back = ButtonWidget.builder(Text.literal("< Back"), button -> {
            playerSubView = PlayerSubView.MAIN;
            rebuildView();
        }).dimensions(x, y, backWidth, backHeight).build();
        this.addDrawableChild(back);
    }

    private void buildPlayerLeaderRequestsView() {
        int backWidth = 80;
        int backHeight = 20;
        int x = this.width - backWidth - 10;
        int y = 10;

        ButtonWidget back = ButtonWidget.builder(Text.literal("< Back"), button -> {
            playerSubView = PlayerSubView.MAIN;
            rebuildView();
        }).dimensions(x, y, backWidth, backHeight).build();
        this.addDrawableChild(back);
    }

    private void buildPlayerTransferOwnershipView() {
        int backWidth = 80;
        int backHeight = 20;
        int x = this.width - backWidth - 10;
        int y = 10;

        ButtonWidget back = ButtonWidget.builder(Text.literal("< Back"), button -> {
            playerSubView = PlayerSubView.MAIN;
            rebuildView();
        }).dimensions(x, y, backWidth, backHeight).build();
        this.addDrawableChild(back);
    }

    // ------------------------
    // ADMIN MAIN VIEW
    // ------------------------

    private void buildAdminMainView() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int x = 10;
        int y = 40;

        adminCreateTeamButton = ButtonWidget.builder(Text.literal("Create Team"), button -> {
            previousView = currentView;
            currentView = ViewMode.ADMIN_CREATE_TEAM;
            rebuildView();
        }).dimensions(x, y, buttonWidth, buttonHeight).build();
        this.addDrawableChild(adminCreateTeamButton);
    }

    // ------------------------
    // ADMIN CREATE TEAM VIEW
    // ------------------------

    private void buildAdminCreateTeamView() {
        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;
        int fieldWidth = 180;
        int fieldHeight = 20;
        int gap = 6;

        // Team name field
        teamNameField = new TextFieldWidget(this.textRenderer, centerX - fieldWidth / 2, startY, fieldWidth, fieldHeight, Text.literal("Team Name"));
        teamNameField.setPlaceholder(Text.literal("Team name"));
        this.addDrawableChild(teamNameField);

        // RGB fields (labels implied by placeholder)
        int rgbFieldX = centerX - fieldWidth / 2 + 40;
        int rgbY = startY + fieldHeight + gap;

        redField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("R"));
        redField.setText("255");
        this.addDrawableChild(redField);

        rgbY += fieldHeight + gap;
        greenField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("G"));
        greenField.setText("255");
        this.addDrawableChild(greenField);

        rgbY += fieldHeight + gap;
        blueField = new TextFieldWidget(this.textRenderer, rgbFieldX, rgbY, 40, fieldHeight, Text.literal("B"));
        blueField.setText("255");
        this.addDrawableChild(blueField);

        // Done + Cancel
        int buttonY = rgbY + fieldHeight + 12;
        createTeamDoneButton = ButtonWidget.builder(Text.literal("Done"), button -> {
            onCreateTeamDone();
        }).dimensions(centerX - 90, buttonY, 80, 20).build();
        this.addDrawableChild(createTeamDoneButton);

        createTeamCancelButton = ButtonWidget.builder(Text.literal("Cancel"), button -> {
            currentView = ViewMode.ADMIN_MAIN;
            rebuildView();
        }).dimensions(centerX + 10, buttonY, 80, 20).build();
        this.addDrawableChild(createTeamCancelButton);
    }

    private void onCreateTeamDone() {
        String name = teamNameField.getText().trim();
        if (name.isEmpty()) {
            sendDebug("Team name cannot be empty");
            return;
        }

        int r = parseColorField(redField);
        int g = parseColorField(greenField);
        int b = parseColorField(blueField);
        int color = (r << 16) | (g << 8) | b;

        // Are we currently in the PLAYER tab's "Create Team" screen?
        boolean fromPlayerPanel =
                (currentView == ViewMode.PLAYER && playerSubView == PlayerSubView.CREATE_TEAM);

        if (fromPlayerPanel) {
            // Player (or admin on Player tab) -> create team with self as leader + member
            EnchainedNetworking.sendCreateTeamSelf(name, color);

            currentView = ViewMode.PLAYER;
            playerSubView = PlayerSubView.MAIN;
        } else {
            // Admin panel -> create an empty team (no leader/members)
            EnchainedNetworking.sendCreateTeam(name, color);

            currentView = ViewMode.ADMIN_MAIN;
        }

        rebuildView();
    }

    private int parseColorField(TextFieldWidget field) {
        try {
            int v = Integer.parseInt(field.getText());
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            return v;
        } catch (NumberFormatException e) {
            return 255;
        }
    }

    // ------------------------
    // ADMIN TEAM MANAGER VIEW
    // ------------------------

    private void buildAdminTeamManagerView() {
        if (selectedTeam == null && !teams.isEmpty()) {
            selectedTeam = teams.get(0);
        }

        // Top-right back
        tmBackButton = ButtonWidget.builder(Text.literal("< Back"), button -> {
            currentView = ViewMode.ADMIN_MAIN;
            rebuildView();
        }).dimensions(this.width - 80 - 10, 10, 80, 20).build();
        this.addDrawableChild(tmBackButton);

        // Bottom-right disband (red)
        tmDisbandButton = ButtonWidget.builder(Text.literal("Disband"), button -> {
            onDisbandTeam();
        }).dimensions(this.width - 90, this.height - 30, 80, 20).build();
        this.addDrawableChild(tmDisbandButton);

        // Lock / Unlock buttons
        tmUnlockButton = ButtonWidget.builder(Text.literal("Unlock"), button -> {
            if (selectedTeam != null) {
                selectedTeam.locked = false;
                EnchainedNetworking.sendSetLocked(selectedTeam.name, false);
                updateLockButtons();
            }
        }).dimensions(this.width - 90 - 90, this.height - 30, 80, 20).build();
        this.addDrawableChild(tmUnlockButton);

        tmLockButton = ButtonWidget.builder(Text.literal("Lock"), button -> {
            if (selectedTeam != null) {
                selectedTeam.locked = true;
                EnchainedNetworking.sendSetLocked(selectedTeam.name, true);
                updateLockButtons();
            }
        }).dimensions(this.width - 90 - 90 - 90, this.height - 30, 80, 20).build();
        this.addDrawableChild(tmLockButton);

        // Bottom-left add player
        tmAddPlayerButton = ButtonWidget.builder(Text.literal("Add Player"), button -> {
            previousView = currentView;
            currentView = ViewMode.ADMIN_ADD_PLAYER;
            rebuildView();
        }).dimensions(10, this.height - 30, 100, 20).build();
        this.addDrawableChild(tmAddPlayerButton);

        updateLockButtons();
    }

    private void updateLockButtons() {
        if (selectedTeam == null) {
            if (tmLockButton != null) tmLockButton.active = false;
            if (tmUnlockButton != null) tmUnlockButton.active = false;
            return;
        }
        boolean locked = selectedTeam.locked;
        if (tmLockButton != null) tmLockButton.active = !locked;
        if (tmUnlockButton != null) tmUnlockButton.active = locked;
    }

    private void onDisbandTeam() {
        if (selectedTeam == null) return;

        String name = selectedTeam.name;

        // Local clear
        for (ClientMember member : selectedTeam.members) {
            for (ClientPlayerInfo info : allPlayers) {
                if (info.uuid.equals(member.uuid)) {
                    info.team = null;
                    break;
                }
            }
        }
        teams.remove(selectedTeam);
        selectedTeam = null;

        EnchainedNetworking.sendDisbandTeam(name);

        currentView = ViewMode.ADMIN_MAIN;
        rebuildView();
    }

    // ------------------------
    // ADMIN ADD PLAYER VIEW
    // ------------------------

    private void buildAdminAddPlayerView() {
        // Top-right back
        apBackButton = ButtonWidget.builder(Text.literal("< Back"), button -> {
            currentView = ViewMode.ADMIN_TEAM_MANAGER;
            rebuildView();
        }).dimensions(this.width - 80 - 10, 10, 80, 20).build();
        this.addDrawableChild(apBackButton);
    }

    // ------------------------
    // Render
    // ------------------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                this.title,
                this.width / 2,
                5,
                0xFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);

        switch (currentView) {
            case PLAYER -> renderPlayerView(context);
            case ADMIN_MAIN -> renderAdminMainView(context);
            case ADMIN_CREATE_TEAM -> renderAdminCreateTeamView(context);
            case ADMIN_TEAM_MANAGER -> renderAdminTeamManagerView(context);
            case ADMIN_ADD_PLAYER -> renderAdminAddPlayerView(context);
        }
    }

    private void renderPlayerView(DrawContext ctx) {
        ClientTeam myTeam = selfInfo != null ? selfInfo.team : null;

        switch (playerSubView) {
            case MAIN -> renderPlayerMainView(ctx, myTeam);
            case CREATE_TEAM -> renderAdminCreateTeamView(ctx); // reuse admin preview
            case JOIN_TEAM -> renderPlayerJoinTeamView(ctx);
            case LEADER_REQUESTS -> renderPlayerLeaderRequestsView(ctx, myTeam);
            case TRANSFER_OWNERSHIP -> renderPlayerTransferOwnershipView(ctx, myTeam);
        }
    }

    private void renderPlayerMainView(DrawContext ctx, @Nullable ClientTeam myTeam) {
        if (myTeam == null) {
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("You are not in a team."),
                    this.width / 2,
                    30,
                    0xFFFFFF
            );
            // buttons are handled in buildPlayerMainView()
            return;
        }

        // In a team -> show member list + leave button and leader controls

        boolean isLeader = selfInfo != null
                && !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Your Team: " + myTeam.name),
                this.width / 2,
                30,
                0xFFFFFF
        );

        // Left side member list (non-draggable)
        int listX = 10;
        int listY = 50;
        int listWidth = this.width / 2 - 20;
        int listHeight = this.height - listY - 40;

        ctx.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80000000);

        int y = listY + 4 - playerMemberListScroll;
        for (int i = 0; i < myTeam.members.size(); i++) {
            ClientMember m = myTeam.members.get(i);

            int entryTop = y;
            int entryBottom = y + playerMemberEntryHeight - 2;
            if (entryBottom < listY || entryTop > listY + listHeight) {
                y += playerMemberEntryHeight;
                continue;
            }

            int entryLeft = listX + 4;
            int entryRight = listX + listWidth - 4;

            boolean memberIsLeader = (i == 0);
            int bgColor = memberIsLeader ? 0x60FFD700 : 0x40FFFFFF;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, bgColor);

            int headSize = playerMemberEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, m.profile);

            int textX = headX + headSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(m.name + (memberIsLeader ? " (Leader)" : "")),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            // Leader-only remove button (greyed if locked)
            if (isLeader && !memberIsLeader) {
                int removeSize = 14;
                int removeX = entryRight - removeSize - 4;
                int removeY = entryTop + (playerMemberEntryHeight - removeSize) / 2;
                int color = myTeam.locked ? 0xFF555555 : 0xFFAA0000;
                ctx.fill(removeX, removeY, removeX + removeSize, removeY + removeSize, color);
                ctx.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal("-"),
                        removeX + removeSize / 2,
                        removeY + (removeSize - this.textRenderer.fontHeight) / 2,
                        0xFFFFFFFF
                );
            }

            y += playerMemberEntryHeight;
        }

        // Right side controls

        int leaveWidth = 80;
        int leaveHeight = 20;
        int leaveX = this.width - leaveWidth - 10;
        int leaveY = 10;

        if (playerLeaveTeamButton == null) {
            playerLeaveTeamButton = ButtonWidget.builder(Text.literal("Leave"), b -> {
                if (myTeam.locked) {
                    sendDebug("This team is locked; you cannot leave.");
                    return;
                }
                EnchainedNetworking.sendLeaveTeam();
            }).dimensions(leaveX, leaveY, leaveWidth, leaveHeight).build();
            this.addDrawableChild(playerLeaveTeamButton);
        }
        playerLeaveTeamButton.active = !myTeam.locked;

        if (isLeader) {
            int buttonWidth = 120;
            int buttonHeight = 20;
            int baseX = this.width - buttonWidth - 10;
            int baseY = 50;
            int gap = 6;

            if (playerViewRequestsButton == null) {
                playerViewRequestsButton = ButtonWidget.builder(Text.literal("View Join Requests"), b -> {
                    playerSubView = PlayerSubView.LEADER_REQUESTS;
                    rebuildView();
                }).dimensions(baseX, baseY, buttonWidth, buttonHeight).build();
                this.addDrawableChild(playerViewRequestsButton);
            }

            if (playerTransferOwnerButton == null) {
                playerTransferOwnerButton = ButtonWidget.builder(Text.literal("Transfer Ownership"), b -> {
                    playerSubView = PlayerSubView.TRANSFER_OWNERSHIP;
                    rebuildView();
                }).dimensions(baseX, baseY + buttonHeight + gap, buttonWidth, buttonHeight).build();
                this.addDrawableChild(playerTransferOwnerButton);
            }

            // Disband button bottom-right
            if (tmDisbandButton == null || currentView != ViewMode.PLAYER) {
                tmDisbandButton = ButtonWidget.builder(Text.literal("Disband"), b -> {
                    if (myTeam.locked) {
                        sendDebug("This team is locked; you cannot disband it.");
                        return;
                    }
                    EnchainedNetworking.sendDisbandTeam(myTeam.name);
                }).dimensions(this.width - 90, this.height - 30, 80, 20).build();
                this.addDrawableChild(tmDisbandButton);
            }
            tmDisbandButton.active = !myTeam.locked;
        }
    }

    private void renderPlayerJoinTeamView(DrawContext ctx) {
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Join a Team"),
                this.width / 2,
                30,
                0xFFFFFF
        );

        int boxX = this.width / 2 - 150;
        int boxY = 50;
        int boxWidth = 300;
        int boxHeight = this.height - boxY - 30;

        ctx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000);

        int y = boxY + 4 - joinTeamScroll;

        for (ClientTeam team : teams) {
            int entryTop = y;
            int entryBottom = y + joinTeamEntryHeight - 2;
            if (entryBottom < boxY || entryTop > boxY + boxHeight) {
                y += joinTeamEntryHeight;
                continue;
            }

            int entryLeft = boxX + 4;
            int entryRight = boxX + boxWidth - 4;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, 0x40FFFFFF);

            int badgeSize = joinTeamEntryHeight - 6;
            int badgeX = entryLeft + 4;
            int badgeY = entryTop + 3;
            int argb = 0xFF000000 | team.color;
            ctx.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, argb);

            String initial = team.name.isEmpty()
                    ? "?"
                    : team.name.substring(0, 1).toUpperCase(Locale.ROOT);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(initial),
                    badgeX + badgeSize / 2,
                    badgeY + (badgeSize - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );

            int textX = badgeX + badgeSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(team.name),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            // "Request sent" tag if we already requested this team
            String key = team.name.toLowerCase(Locale.ROOT);
            if (myPendingJoinTeams.contains(key)) {
                int labelX = entryRight - 70;
                ctx.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal("Requested"),
                        labelX,
                        entryTop + 6,
                        0x55FF55
                );
            }

            y += joinTeamEntryHeight;
        }
    }

    private void renderPlayerLeaderRequestsView(DrawContext ctx, @Nullable ClientTeam myTeam) {
        if (myTeam == null) return;

        boolean isLeader = selfInfo != null
                && !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);

        if (!isLeader) {
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Only the team leader can view join requests."),
                    this.width / 2,
                    this.height / 2,
                    0xAAAAAA
            );
            return;
        }

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Join Requests for " + myTeam.name),
                this.width / 2,
                30,
                0xFFFFFF
        );

        int boxX = this.width / 2 - 180;
        int boxY = 50;
        int boxWidth = 360;
        int boxHeight = this.height - boxY - 30;

        ctx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000);

        int y = boxY + 4 - leaderRequestsScroll;

        for (ClientMember req : myTeam.joinRequests) {
            int entryTop = y;
            int entryBottom = y + leaderRequestsEntryHeight - 2;
            if (entryBottom < boxY || entryTop > boxY + boxHeight) {
                y += leaderRequestsEntryHeight;
                continue;
            }

            int entryLeft = boxX + 4;
            int entryRight = boxX + boxWidth - 4;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, 0x40FFFFFF);

            int headSize = leaderRequestsEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, req.profile);

            int textX = headX + headSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(req.name),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            // Accept / Reject buttons (just drawn; clicks handled separately)
            int buttonWidth = 50;
            int buttonHeight = leaderRequestsEntryHeight - 6;
            int rejectX = entryRight - buttonWidth - 4;
            int acceptX = rejectX - buttonWidth - 4;
            int buttonY = entryTop + 3;

            // Accept button (greyed if locked)
            int acceptColor = myTeam.locked ? 0xFF555555 : 0xFF00AA00;
            ctx.fill(acceptX, buttonY, acceptX + buttonWidth, buttonY + buttonHeight, acceptColor);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Accept"),
                    acceptX + buttonWidth / 2,
                    buttonY + (buttonHeight - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );

            // Reject button (always active)
            int rejectColor = 0xFFAA0000;
            ctx.fill(rejectX, buttonY, rejectX + buttonWidth, buttonY + buttonHeight, rejectColor);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Reject"),
                    rejectX + buttonWidth / 2,
                    buttonY + (buttonHeight - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );

            y += leaderRequestsEntryHeight;
        }
    }

    private void renderPlayerTransferOwnershipView(DrawContext ctx, @Nullable ClientTeam myTeam) {
        if (myTeam == null) return;

        boolean isLeader = selfInfo != null
                && !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);

        if (!isLeader) {
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Only the team leader can transfer ownership."),
                    this.width / 2,
                    this.height / 2,
                    0xAAAAAA
            );
            return;
        }

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Transfer Ownership of " + myTeam.name),
                this.width / 2,
                30,
                0xFFFFFF
        );

        int boxX = this.width / 2 - 180;
        int boxY = 50;
        int boxWidth = 360;
        int boxHeight = this.height - boxY - 30;

        ctx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000);

        int y = boxY + 4 - transferOwnershipScroll;

        for (int i = 0; i < myTeam.members.size(); i++) {
            ClientMember m = myTeam.members.get(i);

            int entryTop = y;
            int entryBottom = y + transferOwnershipEntryHeight - 2;
            if (entryBottom < boxY || entryTop > boxY + boxHeight) {
                y += transferOwnershipEntryHeight;
                continue;
            }

            int entryLeft = boxX + 4;
            int entryRight = boxX + boxWidth - 4;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, 0x40FFFFFF);

            int headSize = transferOwnershipEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, m.profile);

            int textX = headX + headSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(m.name + (i == 0 ? " (Current Leader)" : "")),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            y += transferOwnershipEntryHeight;
        }

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Click a member to make them the new leader."),
                this.width / 2,
                boxY + boxHeight + 4,
                0xAAAAAA
        );
    }

    private void renderAdminMainView(DrawContext ctx) {
        // Team list on the right in a scrollable box
        int boxX = this.width / 2;
        int boxY = 40;
        int boxWidth = this.width - boxX - 10;
        int boxHeight = this.height - boxY - 10;

        ctx.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0x80000000);

        int y = boxY + 4 - teamListScroll;

        for (ClientTeam team : teams) {
            if (y + teamEntryHeight < boxY || y > boxY + boxHeight) {
                y += teamEntryHeight;
                continue;
            }

            int entryLeft = boxX + 4;
            int entryRight = boxX + boxWidth - 4;
            int entryTop = y;
            int entryBottom = y + teamEntryHeight - 2;

            int bgColor = (team == selectedTeam) ? 0x60FFFFFF : 0x40FFFFFF;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, bgColor);

            // Color badge
            int badgeSize = teamEntryHeight - 6;
            int badgeX = entryLeft + 4;
            int badgeY = entryTop + 3;
            int argb = 0xFF000000 | team.color;
            ctx.fill(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize, argb);

            String initial = team.name.isEmpty() ? "?" : team.name.substring(0, 1).toUpperCase(Locale.ROOT);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(initial),
                    badgeX + badgeSize / 2,
                    badgeY + (badgeSize - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );

            // Team name
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(team.name),
                    badgeX + badgeSize + 6,
                    entryTop + 6,
                    0xFFFFFF
            );

            y += teamEntryHeight;
        }
    }

    private void renderAdminCreateTeamView(DrawContext ctx) {
        int centerX = this.width / 2;
        int previewSize = 40;
        int previewY = this.height / 2 - 18;

        int r = parseColorField(redField);
        int g = parseColorField(greenField);
        int b = parseColorField(blueField);
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;

        int previewX = centerX - previewSize / 2 + 50;
        ctx.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, color);

        String name = teamNameField.getText().trim();
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(initial),
                previewX + previewSize / 2,
                previewY + (previewSize - this.textRenderer.fontHeight) / 2,
                0xFFFFFFFF
        );

        if (redField != null) {
            int labelX = redField.getX() - 12;
            int labelY = redField.getY() + (redField.getHeight() - this.textRenderer.fontHeight) / 2;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("R"), labelX, labelY, 0xFFFFFF);
        }

        if (greenField != null) {
            int labelX = greenField.getX() - 12;
            int labelY = greenField.getY() + (greenField.getHeight() - this.textRenderer.fontHeight) / 2;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("G"), labelX, labelY, 0xFFFFFF);
        }

        if (blueField != null) {
            int labelX = blueField.getX() - 12;
            int labelY = blueField.getY() + (blueField.getHeight() - this.textRenderer.fontHeight) / 2;
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("B"), labelX, labelY, 0xFFFFFF);
        }
    }

    private void renderAdminTeamManagerView(DrawContext ctx) {
        if (selectedTeam == null) {
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No team selected"),
                    this.width / 2,
                    this.height / 2,
                    0xAAAAAA
            );
            return;
        }

        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Managing Team: " + selectedTeam.name),
                this.width / 2,
                30,
                0xFFFFFF
        );

        int listX = 10;
        int listY = 50;
        int listWidth = this.width / 2 - 20;
        int listHeight = this.height - listY - 40;

        ctx.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80000000);

        List<ClientMember> members = selectedTeam.members;
        int size = members.size();

        // Clean up anim map (in case players were removed)
        memberAnimY.keySet().removeIf(uuid -> members.stream().noneMatch(m -> m.uuid.equals(uuid)));

        // Compute hover index (where the dragged item would land)
        int hoverIndex = -1;
        if (draggingMemberIndex >= 0 && draggingMemberIndex < size) {
            double localY = lastMouseY - listY - 4 + memberListScroll;
            hoverIndex = (int) (localY / memberEntryHeight);
            if (hoverIndex < 0) hoverIndex = 0;
            if (hoverIndex >= size) hoverIndex = size - 1;
        }

        // Draw all non-dragging members with animated Y, leaving a gap at hoverIndex
        for (int i = 0; i < size; i++) {
            ClientMember member = members.get(i);
            if (i == draggingMemberIndex) {
                // dragged member is drawn last as a ghost
                continue;
            }

            // Compute logical slot index with push-out effect
            int logicalIndex = i;
            if (draggingMemberIndex >= 0 && hoverIndex != -1) {
                if (i > draggingMemberIndex && i <= hoverIndex) {
                    logicalIndex = i - 1;
                } else if (i < draggingMemberIndex && i >= hoverIndex) {
                    logicalIndex = i + 1;
                }
            }

            int targetTop = listY + 4 - memberListScroll + logicalIndex * memberEntryHeight;
            float currentTop = memberAnimY.compute(member.uuid, (uuid, prev) -> {
                float t = (prev == null ? targetTop : prev);
                float speed = 0.35f; // smoothing factor
                return t + (float) ((targetTop - t) * speed);
            });

            // Cull outside visible area
            if (currentTop + memberEntryHeight < listY || currentTop > listY + listHeight) {
                continue;
            }

            int entryLeft = listX + 4;
            int entryRight = listX + listWidth - 4;
            int entryTop = (int) currentTop;
            int entryBottom = entryTop + memberEntryHeight - 2;

            boolean isLeader = (i == 0);
            int bgColor = isLeader ? 0x60FFD700 : 0x40FFFFFF;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, bgColor);

            // Player head
            int headSize = memberEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, member.profile);

            // Name
            int textX = headX + headSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(member.name + (isLeader ? " (Leader)" : "")),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            // Remove button
            int removeSize = 14;
            int removeX = entryRight - removeSize - 4;
            int removeY = entryTop + (memberEntryHeight - removeSize) / 2;
            ctx.fill(removeX, removeY, removeX + removeSize, removeY + removeSize, 0xFFAA0000);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("-"),
                    removeX + removeSize / 2,
                    removeY + (removeSize - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );
        }

        // Draw the dragged member as a ghost overlay following the mouse
        if (draggingMemberIndex >= 0 && draggingMemberIndex < size) {
            ClientMember member = members.get(draggingMemberIndex);

            int ghostTop = (int) (draggingMouseY - draggingMouseYOffset);
            // Clamp ghost to list area
            if (ghostTop < listY + 4) ghostTop = listY + 4;
            if (ghostTop > listY + listHeight - memberEntryHeight) ghostTop = listY + listHeight - memberEntryHeight;

            int entryLeft = listX + 4;
            int entryRight = listX + listWidth - 4;
            int entryTop = ghostTop;
            int entryBottom = entryTop + memberEntryHeight - 2;

            boolean isLeader = (draggingMemberIndex == 0);
            int bgColor = isLeader ? 0x90FFD700 : 0x80FFFFFF;
            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, bgColor);

            // Slight outline to emphasize drag
            ctx.fill(entryLeft, entryTop, entryRight, entryTop + 1, 0xFFFFFFFF);
            ctx.fill(entryLeft, entryBottom - 1, entryRight, entryBottom, 0xFFFFFFFF);

            // Player head
            int headSize = memberEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, member.profile);

            // Name
            int textX = headX + headSize + 6;
            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(member.name + (isLeader ? " (Leader)" : "")),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            // Remove button (still visible on ghost)
            int removeSize = 14;
            int removeX = entryRight - removeSize - 4;
            int removeY = entryTop + (memberEntryHeight - removeSize) / 2;
            ctx.fill(removeX, removeY, removeX + removeSize, removeY + removeSize, 0xFFCC0000);
            ctx.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("-"),
                    removeX + removeSize / 2,
                    removeY + (removeSize - this.textRenderer.fontHeight) / 2,
                    0xFFFFFFFF
            );
        }
    }

    private void renderAdminAddPlayerView(DrawContext ctx) {
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Add Player to Team: " + (selectedTeam != null ? selectedTeam.name : "?")),
                this.width / 2,
                30,
                0xFFFFFF
        );

        int listX = 10;
        int listY = 50;
        int listWidth = this.width - 20;
        int listHeight = this.height - listY - 20;

        ctx.fill(listX, listY, listX + listWidth, listY + listHeight, 0x80000000);

        int y = listY + 4 - addPlayerScroll;

        for (ClientPlayerInfo info : allPlayers) {
            if (y + addPlayerEntryHeight < listY || y > listY + listHeight) {
                y += addPlayerEntryHeight;
                continue;
            }

            int entryLeft = listX + 4;
            int entryRight = listX + listWidth - 4;
            int entryTop = y;
            int entryBottom = y + addPlayerEntryHeight - 2;

            ctx.fill(entryLeft, entryTop, entryRight, entryBottom, 0x40FFFFFF);

            int headSize = addPlayerEntryHeight - 6;
            int headX = entryLeft + 4;
            int headY = entryTop + 3;
            drawPlayerHead(ctx, headX, headY, headSize, info.profile);

            int textX = headX + headSize + 6;

            // Team badge
            if (info.team != null) {
                int badgeX = textX;
                int argb = 0xFF000000 | info.team.color;
                ctx.fill(badgeX, headY, badgeX + headSize, headY + headSize, argb);

                String initial = info.team.name.isEmpty() ? "?" : info.team.name.substring(0, 1).toUpperCase(Locale.ROOT);
                ctx.drawCenteredTextWithShadow(
                        this.textRenderer,
                        Text.literal(initial),
                        badgeX + headSize / 2,
                        headY + (headSize - this.textRenderer.fontHeight) / 2,
                        0xFFFFFFFF
                );

                textX = badgeX + headSize + 6;
            }

            ctx.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(info.name),
                    textX,
                    entryTop + 6,
                    0xFFFFFF
            );

            y += addPlayerEntryHeight;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------
    // Mouse / scroll handling
    // ------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (currentView == ViewMode.PLAYER && button == 0) {
            if (handlePlayerClick(mouseX, mouseY)) {
                return true;
            }
        }

        if (currentView == ViewMode.ADMIN_MAIN && button == 0) {
            handleAdminMainClick(mouseX, mouseY);
        }

        if (currentView == ViewMode.ADMIN_TEAM_MANAGER && button == 0) {
            if (handleMemberListClick(mouseX, mouseY)) {
                return true;
            }
        }

        if (currentView == ViewMode.ADMIN_ADD_PLAYER && button == 0) {
            if (handleAddPlayerClick(mouseX, mouseY)) {
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePlayerClick(double mouseX, double mouseY) {
        ClientTeam myTeam = selfInfo != null ? selfInfo.team : null;

        switch (playerSubView) {
            case MAIN -> {
                if (myTeam != null) {
                    return handlePlayerTeamMainClick(mouseX, mouseY, myTeam);
                }
                // No team: buttons are regular widgets, so no extra handling needed
                return false;
            }
            case JOIN_TEAM -> {
                return handleJoinTeamClick(mouseX, mouseY);
            }
            case LEADER_REQUESTS -> {
                return handleLeaderRequestsClick(mouseX, mouseY, myTeam);
            }
            case TRANSFER_OWNERSHIP -> {
                return handleTransferOwnershipClick(mouseX, mouseY, myTeam);
            }
            default -> {
                return false;
            }
        }
    }

    private boolean handlePlayerTeamMainClick(double mouseX, double mouseY, ClientTeam myTeam) {
        if (selfInfo == null) return false;

        boolean isLeader = !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);

        if (!isLeader || myTeam.locked) {
            return false;
        }

        int listX = 10;
        int listY = 50;
        int listWidth = this.width / 2 - 20;
        int listHeight = this.height - listY - 40;

        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) {
            return false;
        }

        int y = listY + 4 - playerMemberListScroll;

        for (int i = 0; i < myTeam.members.size(); i++) {
            ClientMember m = myTeam.members.get(i);
            boolean memberIsLeader = (i == 0);

            int entryTop = y;
            int entryBottom = y + playerMemberEntryHeight - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                if (memberIsLeader) {
                    return false; // can't remove self here
                }

                int entryLeft = listX + 4;
                int entryRight = listX + listWidth - 4;

                int removeSize = 14;
                int removeX = entryRight - removeSize - 4;
                int removeY = entryTop + (playerMemberEntryHeight - removeSize) / 2;

                if (mouseX >= removeX && mouseX <= removeX + removeSize && mouseY >= removeY && mouseY <= removeY + removeSize) {
                    // remove player from team
                    myTeam.members.remove(i);
                    EnchainedNetworking.sendRemovePlayerFromTeam(m.uuid);
                    return true;
                }
            }

            y += playerMemberEntryHeight;
        }

        return false;
    }

    private boolean handleJoinTeamClick(double mouseX, double mouseY) {
        int boxX = this.width / 2 - 150;
        int boxY = 50;
        int boxWidth = 300;
        int boxHeight = this.height - boxY - 30;

        if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < boxY || mouseY > boxY + boxHeight) {
            return false;
        }

        int y = boxY + 4 - joinTeamScroll;

        for (ClientTeam team : teams) {
            int entryTop = y;
            int entryBottom = y + joinTeamEntryHeight - 2;
            if (mouseY >= entryTop && mouseY <= entryBottom) {
                String key = team.name.toLowerCase(Locale.ROOT);
                if (myPendingJoinTeams.contains(key)) {
                    sendDebug("Already requested to join " + team.name);
                    return true;
                }

                myPendingJoinTeams.add(key);
                EnchainedNetworking.sendJoinTeamRequest(team.name);
                return true;
            }
            y += joinTeamEntryHeight;
        }

        return false;
    }

    private boolean handleLeaderRequestsClick(double mouseX, double mouseY, @Nullable ClientTeam myTeam) {
        if (myTeam == null || selfInfo == null) return false;

        boolean isLeader = !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);
        if (!isLeader) return false;

        int boxX = this.width / 2 - 180;
        int boxY = 50;
        int boxWidth = 360;
        int boxHeight = this.height - boxY - 30;

        if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < boxY || mouseY > boxY + boxHeight) {
            return false;
        }

        int y = boxY + 4 - leaderRequestsScroll;

        for (int i = 0; i < myTeam.joinRequests.size(); i++) {
            ClientMember req = myTeam.joinRequests.get(i);

            int entryTop = y;
            int entryBottom = y + leaderRequestsEntryHeight - 2;
            if (mouseY >= entryTop && mouseY <= entryBottom) {
                int entryLeft = boxX + 4;
                int entryRight = boxX + boxWidth - 4;

                int buttonWidth = 50;
                int buttonHeight = leaderRequestsEntryHeight - 6;
                int rejectX = entryRight - buttonWidth - 4;
                int acceptX = rejectX - buttonWidth - 4;
                int buttonY = entryTop + 3;

                boolean onAccept = mouseX >= acceptX && mouseX <= acceptX + buttonWidth
                        && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;
                boolean onReject = mouseX >= rejectX && mouseX <= rejectX + buttonWidth
                        && mouseY >= buttonY && mouseY <= buttonY + buttonHeight;

                if (onAccept && !myTeam.locked) {
                    EnchainedNetworking.sendAcceptJoinRequest(myTeam.name, req.uuid);
                    // locally move them into team members
                    myTeam.joinRequests.remove(i);
                    myTeam.members.add(req);
                    return true;
                }
                if (onReject) {
                    EnchainedNetworking.sendRejectJoinRequest(myTeam.name, req.uuid);
                    myTeam.joinRequests.remove(i);
                    return true;
                }
            }

            y += leaderRequestsEntryHeight;
        }

        return false;
    }

    private boolean handleTransferOwnershipClick(double mouseX, double mouseY, @Nullable ClientTeam myTeam) {
        if (myTeam == null || selfInfo == null) return false;

        boolean isLeader = !myTeam.members.isEmpty()
                && myTeam.members.get(0).uuid.equals(selfInfo.uuid);
        if (!isLeader) return false;

        int boxX = this.width / 2 - 180;
        int boxY = 50;
        int boxWidth = 360;
        int boxHeight = this.height - boxY - 30;

        if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < boxY || mouseY > boxY + boxHeight) {
            return false;
        }

        int y = boxY + 4 - transferOwnershipScroll;

        for (int i = 0; i < myTeam.members.size(); i++) {
            ClientMember m = myTeam.members.get(i);

            int entryTop = y;
            int entryBottom = y + transferOwnershipEntryHeight - 2;
            if (mouseY >= entryTop && mouseY <= entryBottom) {
                if (i == 0) {
                    sendDebug("You are already the leader.");
                    return true;
                }

                EnchainedNetworking.sendTransferOwnership(myTeam.name, m.uuid);
                // let server handle actual leader change; we'll see it on next sync
                playerSubView = PlayerSubView.MAIN;
                rebuildView();
                return true;
            }

            y += transferOwnershipEntryHeight;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (currentView == ViewMode.ADMIN_TEAM_MANAGER && button == 0) {
            if (draggingMemberIndex >= 0 && selectedTeam != null) {
                // update drag position for ghost box
                this.draggingMouseY = mouseY;
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (currentView == ViewMode.ADMIN_TEAM_MANAGER && button == 0) {
            if (draggingMemberIndex >= 0 && selectedTeam != null) {
                handleMemberDrop(mouseX, mouseY);
                draggingMemberIndex = -1;
                return true;
            }
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int scroll = (int) (-amount * 10);

        switch (currentView) {
            case ADMIN_MAIN -> {
                int boxX = this.width / 2;
                int boxY = 40;
                int boxWidth = this.width - boxX - 10;
                int boxHeight = this.height - boxY - 10;

                if (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) {
                    int max = Math.max(0, teams.size() * teamEntryHeight - boxHeight);
                    teamListScroll = Math.max(0, Math.min(max, teamListScroll + scroll));
                    return true;
                }
            }
            case ADMIN_TEAM_MANAGER -> {
                int listX = 10;
                int listY = 50;
                int listWidth = this.width / 2 - 20;
                int listHeight = this.height - listY - 40;

                if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                    int max = selectedTeam == null ? 0 : Math.max(0, selectedTeam.members.size() * memberEntryHeight - listHeight);
                    memberListScroll = Math.max(0, Math.min(max, memberListScroll + scroll));
                    return true;
                }
            }
            case ADMIN_ADD_PLAYER -> {
                int listX = 10;
                int listY = 50;
                int listWidth = this.width - 20;
                int listHeight = this.height - listY - 20;

                if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                    int max = Math.max(0, allPlayers.size() * addPlayerEntryHeight - listHeight);
                    addPlayerScroll = Math.max(0, Math.min(max, addPlayerScroll + scroll));
                    return true;
                }
            }
            case PLAYER -> {
                ClientTeam myTeam = selfInfo != null ? selfInfo.team : null;

                switch (playerSubView) {
                    case MAIN -> {
                        if (myTeam != null) {
                            int listX = 10;
                            int listY = 50;
                            int listWidth = this.width / 2 - 20;
                            int listHeight = this.height - listY - 40;

                            if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
                                int max = Math.max(0, myTeam.members.size() * playerMemberEntryHeight - listHeight);
                                playerMemberListScroll = Math.max(0, Math.min(max, playerMemberListScroll + scroll));
                                return true;
                            }
                        }
                    }
                    case JOIN_TEAM -> {
                        int boxX = this.width / 2 - 150;
                        int boxY = 50;
                        int boxWidth = 300;
                        int boxHeight = this.height - boxY - 30;

                        if (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) {
                            int max = Math.max(0, teams.size() * joinTeamEntryHeight - boxHeight);
                            joinTeamScroll = Math.max(0, Math.min(max, joinTeamScroll + scroll));
                            return true;
                        }
                    }
                    case LEADER_REQUESTS -> {
                        ClientTeam team = myTeam;
                        if (team != null) {
                            int boxX = this.width / 2 - 180;
                            int boxY = 50;
                            int boxWidth = 360;
                            int boxHeight = this.height - boxY - 30;

                            if (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) {
                                int max = Math.max(0, team.joinRequests.size() * leaderRequestsEntryHeight - boxHeight);
                                leaderRequestsScroll = Math.max(0, Math.min(max, leaderRequestsScroll + scroll));
                                return true;
                            }
                        }
                    }
                    case TRANSFER_OWNERSHIP -> {
                        ClientTeam team = myTeam;
                        if (team != null) {
                            int boxX = this.width / 2 - 180;
                            int boxY = 50;
                            int boxWidth = 360;
                            int boxHeight = this.height - boxY - 30;

                            if (mouseX >= boxX && mouseX <= boxX + boxWidth && mouseY >= boxY && mouseY <= boxY + boxHeight) {
                                int max = Math.max(0, team.members.size() * transferOwnershipEntryHeight - boxHeight);
                                transferOwnershipScroll = Math.max(0, Math.min(max, transferOwnershipScroll + scroll));
                                return true;
                            }
                        }
                    }
                    default -> {}
                }
            }
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    private void handleAdminMainClick(double mouseX, double mouseY) {
        int boxX = this.width / 2;
        int boxY = 40;
        int boxWidth = this.width - boxX - 10;
        int boxHeight = this.height - boxY - 10;

        if (mouseX < boxX || mouseX > boxX + boxWidth || mouseY < boxY || mouseY > boxY + boxHeight) {
            return;
        }

        int y = boxY + 4 - teamListScroll;
        for (ClientTeam team : teams) {
            if (mouseY >= y && mouseY <= y + teamEntryHeight) {
                selectedTeam = team;
                currentView = ViewMode.ADMIN_TEAM_MANAGER;
                rebuildView();
                return;
            }
            y += teamEntryHeight;
        }
    }

    private boolean handleMemberListClick(double mouseX, double mouseY) {
        if (selectedTeam == null) return false;

        int listX = 10;
        int listY = 50;
        int listWidth = this.width / 2 - 20;
        int listHeight = this.height - listY - 40;

        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) {
            return false;
        }

        int y = listY + 4 - memberListScroll;
        List<ClientMember> members = selectedTeam.members;
        for (int i = 0; i < members.size(); i++) {
            int entryTop = y;
            int entryBottom = y + memberEntryHeight - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                int entryLeft = listX + 4;
                int entryRight = listX + listWidth - 4;

                int removeSize = 14;
                int removeX = entryRight - removeSize - 4;
                int removeY = entryTop + (memberEntryHeight - removeSize) / 2;

                if (mouseX >= removeX && mouseX <= removeX + removeSize && mouseY >= removeY && mouseY <= removeY + removeSize) {
                    // Remove member locally
                    ClientMember removed = members.remove(i);

                    for (ClientPlayerInfo info : allPlayers) {
                        if (info.uuid.equals(removed.uuid)) {
                            info.team = null;
                            break;
                        }
                    }

                    // Tell server: this player is now in no team
                    EnchainedNetworking.sendRemovePlayerFromTeam(removed.uuid);

                    if (i == 0 && !members.isEmpty()) {
                        // new leader is at index 0; we'll sync explicitly
                        ClientMember newLeader = members.get(0);
                        EnchainedNetworking.sendSetLeader(selectedTeam.name, newLeader.uuid);
                    }

                    return true;
                } else {
                    // Start drag
                    draggingMemberIndex = i;
                    draggingMouseYOffset = (int) (mouseY - entryTop);
                    draggingMouseY = mouseY;
                    return true;
                }
            }

            y += memberEntryHeight;
        }

        return false;
    }

    private void handleMemberDrop(double mouseX, double mouseY) {
        if (selectedTeam == null) return;
        if (draggingMemberIndex < 0 || draggingMemberIndex >= selectedTeam.members.size()) return;

        int listX = 10;
        int listY = 50;
        int listWidth = this.width / 2 - 20;
        int listHeight = this.height - listY - 40;

        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) {
            return; // dropped outside
        }

        int y = listY + 4 - memberListScroll;
        int size = selectedTeam.members.size();
        int targetIndex = size - 1;

        for (int i = 0; i < size; i++) {
            int mid = y + memberEntryHeight / 2;
            if (mouseY < mid) {
                targetIndex = i;
                break;
            }
            y += memberEntryHeight;
        }

        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex == draggingMemberIndex) return;

        ClientMember member = selectedTeam.members.remove(draggingMemberIndex);
        selectedTeam.members.add(targetIndex, member);

        // Leader changed if index 0 changed
        if (targetIndex == 0 || draggingMemberIndex == 0) {
            ClientMember newLeader = selectedTeam.members.get(0);
            EnchainedNetworking.sendSetLeader(selectedTeam.name, newLeader.uuid);
        }
    }

    private boolean handleAddPlayerClick(double mouseX, double mouseY) {
        int listX = 10;
        int listY = 50;
        int listWidth = this.width - 20;
        int listHeight = this.height - listY - 20;

        if (mouseX < listX || mouseX > listX + listWidth || mouseY < listY || mouseY > listY + listHeight) {
            return false;
        }

        int y = listY + 4 - addPlayerScroll;
        for (ClientPlayerInfo info : allPlayers) {
            int entryTop = y;
            int entryBottom = y + addPlayerEntryHeight - 2;
            if (mouseY >= entryTop && mouseY <= entryBottom) {
                onAddPlayerSelected(info);
                return true;
            }
            y += addPlayerEntryHeight;
        }
        return false;
    }

    private void onAddPlayerSelected(ClientPlayerInfo info) {
        if (selectedTeam == null) return;

        // If they are already in *this* team, do nothing
        for (ClientMember member : selectedTeam.members) {
            if (member.uuid.equals(info.uuid)) {
                sendDebug(info.name + " is already in this team");
                return;
            }
        }

        // Local move
        if (info.team != null) {
            ClientTeam previousTeam = info.team;
            previousTeam.members.removeIf(m -> m.uuid.equals(info.uuid));
            info.team = null;
        }

        selectedTeam.members.add(new ClientMember(info.uuid, info.name, info.profile));
        info.team = selectedTeam;

        // Tell server to move them: remove from old team (if any) + add to selected
        EnchainedNetworking.sendMovePlayerToTeam(selectedTeam.name, info.uuid);

        currentView = ViewMode.ADMIN_TEAM_MANAGER;
        rebuildView();
    }

    // ------------------------
    // Player head rendering
    // ------------------------

    private void drawPlayerHead(DrawContext ctx, int x, int y, int size, GameProfile profile) {
        Identifier skin = EnchainedPlayerSkins.getOrRequestFace(profile);

        // Draw base layer (the face)
        ctx.drawTexture(
                skin,
                x, y,                // screen position
                size, size,          // draw size
                8, 8,                // u, v in 64x64 skin
                8, 8,                // region width/height
                64, 64               // texture atlas size
        );

        // Draw hat/overlay layer (if present)
        ctx.drawTexture(
                skin,
                x, y,
                size, size,
                40, 8,               // overlay UV
                8, 8,
                64, 64
        );
    }

    // ------------------------
    // Helpers + client models
    // ------------------------

    private void sendDebug(String msg) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.inGameHud != null) {
            client.inGameHud.getChatHud().addMessage(Text.literal("[Enchained] " + msg));
        }
    }

    private static class ClientTeam {
        final String name;
        int color; // 0xRRGGBB
        boolean locked = false;
        final List<ClientMember> members = new ArrayList<>();
        // players who requested to join this team (leader view)
        final List<ClientMember> joinRequests = new ArrayList<>();

        ClientTeam(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    private static class ClientMember {
        final UUID uuid;
        final String name;
        final GameProfile profile;

        ClientMember(UUID uuid, String name, GameProfile profile) {
            this.uuid = uuid;
            this.name = name;
            this.profile = profile;
        }
    }

    private static class ClientPlayerInfo {
        final UUID uuid;
        final String name;
        final GameProfile profile;
        @Nullable ClientTeam team;

        ClientPlayerInfo(UUID uuid, String name, GameProfile profile, @Nullable ClientTeam team) {
            this.uuid = uuid;
            this.name = name;
            this.profile = profile;
            this.team = team;
        }
    }
}