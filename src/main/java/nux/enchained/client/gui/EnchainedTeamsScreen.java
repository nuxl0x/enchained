package nux.enchained.client.gui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
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

        // Attach members to teams
        for (TeamSnapshot snap : teamSnapshots) {
            ClientTeam ct = teamsByKey.get(snap.name.toLowerCase(Locale.ROOT));
            if (ct == null) continue;

            for (UUID memberId : snap.members) {
                ClientPlayerInfo info = playerById.get(memberId);
                if (info == null) continue;
                ClientMember cm = new ClientMember(info.uuid, info.name, info.profile);
                ct.members.add(cm);
                info.team = ct;
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

        // Recreate tabs (if admin)
        setupTabs();

        switch (currentView) {
            case PLAYER -> buildPlayerView();
            case ADMIN_MAIN -> buildAdminMainView();
            case ADMIN_CREATE_TEAM -> buildAdminCreateTeamView();
            case ADMIN_TEAM_MANAGER -> buildAdminTeamManagerView();
            case ADMIN_ADD_PLAYER -> buildAdminAddPlayerView();
        }
    }

    // ------------------------
    // PLAYER VIEW (placeholder for now)
    // ------------------------

    private void buildPlayerView() {
        // No extra widgets yet; just a placeholder text in render().
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

        EnchainedNetworking.sendCreateTeam(name, color);

        currentView = ViewMode.ADMIN_MAIN;
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
        ctx.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Player team controls will go here."),
                this.width / 2,
                this.height / 2,
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