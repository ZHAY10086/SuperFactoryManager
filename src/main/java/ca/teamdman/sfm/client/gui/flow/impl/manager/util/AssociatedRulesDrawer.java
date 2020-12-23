/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package ca.teamdman.sfm.client.gui.flow.impl.manager.util;

import ca.teamdman.sfm.client.gui.flow.core.BaseScreen;
import ca.teamdman.sfm.client.gui.flow.core.Colour3f.CONST;
import ca.teamdman.sfm.client.gui.flow.core.Size;
import ca.teamdman.sfm.client.gui.flow.impl.manager.FlowTileEntityRule;
import ca.teamdman.sfm.client.gui.flow.impl.manager.core.ManagerFlowController;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowContainer;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowDrawer;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowItemStack;
import ca.teamdman.sfm.client.gui.flow.impl.util.FlowPlusButton;
import ca.teamdman.sfm.common.config.Config;
import ca.teamdman.sfm.common.flow.data.core.FlowData;
import ca.teamdman.sfm.common.flow.data.core.Position;
import ca.teamdman.sfm.common.flow.data.impl.RuleFlowData;
import ca.teamdman.sfm.common.net.PacketHandler;
import ca.teamdman.sfm.common.net.packet.manager.ManagerCreateTileEntityRulePacketC2S;
import com.mojang.blaze3d.matrix.MatrixStack;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;

public abstract class AssociatedRulesDrawer extends FlowContainer {

	private final ManagerFlowController CONTROLLER;
	private final FlowDrawer CHILDREN_RULES_DRAWER;
	private final FlowDrawer SELECTION_RULES_DRAWER;

	public AssociatedRulesDrawer(ManagerFlowController controller, Position pos) {
		super(pos, new Size(0, 0));
		this.CONTROLLER = controller;
		this.CHILDREN_RULES_DRAWER = new FlowDrawer(
			new Position(),
			FlowItemStack.ITEM_TOTAL_WIDTH,
			FlowItemStack.ITEM_TOTAL_HEIGHT
		);
		this.SELECTION_RULES_DRAWER = new FlowDrawer(
			CHILDREN_RULES_DRAWER.getPosition().withConstantOffset(
				() -> CHILDREN_RULES_DRAWER.getSize().getWidth() + 10,
				() -> 0
			),
			FlowItemStack.ITEM_TOTAL_WIDTH,
			FlowItemStack.ITEM_TOTAL_HEIGHT
		);

		SELECTION_RULES_DRAWER.setVisible(false);
		SELECTION_RULES_DRAWER.setEnabled(false);
		SELECTION_RULES_DRAWER.setDraggable(false);
		CHILDREN_RULES_DRAWER.setDraggable(false);
		rebuildChildrenDrawer();
		rebuildSelectionDrawer();
		addChild(CHILDREN_RULES_DRAWER);
		addChild(SELECTION_RULES_DRAWER);
	}

	public abstract List<RuleFlowData> getChildrenRules();

	public abstract void setChildrenRules(List<UUID> rules);

	public abstract List<RuleFlowData> getSelectableRules();

	public void rebuildChildrenDrawer() {
		CHILDREN_RULES_DRAWER.getChildren().clear();
		CHILDREN_RULES_DRAWER.addChild(new EditChildrenButton());
		getChildrenRules().stream()
			.map(ChildRulesDrawerItem::new)
			.forEach(CHILDREN_RULES_DRAWER::addChild);
		CHILDREN_RULES_DRAWER.update();
	}

	public void rebuildSelectionDrawer() {
		SELECTION_RULES_DRAWER.getChildren().clear();
		SELECTION_RULES_DRAWER.addChild(new AddRuleButton());

		getSelectableRules().stream()
			.map(SelectionRulesDrawerItem::new)
			.forEach(SELECTION_RULES_DRAWER::addChild);

		// Ensure children rules are selected in the global rule drawer
		Set<UUID> selected = getChildrenRules().stream()
			.map(FlowData::getId)
			.collect(Collectors.toSet());
		SELECTION_RULES_DRAWER.getChildren().stream()
			.filter(c -> c instanceof SelectionRulesDrawerItem)
			.map(c -> ((SelectionRulesDrawerItem) c))
			.filter(c -> selected.contains(c.DATA.getId()))
			.forEach(c -> c.setSelected(true));

		SELECTION_RULES_DRAWER.update();
	}

	@Override
	public void draw(
		BaseScreen screen, MatrixStack matrixStack, int mx, int my, float deltaTime
	) {
		super.draw(screen, matrixStack, mx, my, deltaTime);
	}

	private class ChildRulesDrawerItem extends FlowItemStack {

		public RuleFlowData DATA;

		public ChildRulesDrawerItem(
			RuleFlowData data
		) {
			super(data.getIcon(), new Position());
			this.DATA = data;
			setDraggable(false);
		}

		@Override
		public void onSelectionChanged() {
			if (!Config.allowMultipleRuleWindows) {
				CONTROLLER.getChildren().stream()
					.filter(c -> c instanceof FlowTileEntityRule)
					.map(c -> ((FlowTileEntityRule) c))
					.forEach(c -> {
						c.setVisible(false);
						c.setEnabled(false);
					});
				CHILDREN_RULES_DRAWER.getChildren().stream()
					.filter(c -> c instanceof ChildRulesDrawerItem)
					.forEach(c -> ((ChildRulesDrawerItem) c).setSelected(false));
			}
			CONTROLLER.findFirstChild(DATA.getId()).ifPresent(comp -> {
				comp.setVisible(isSelected());
				comp.setEnabled(isSelected());
			});
		}
	}

	private class SelectionRulesDrawerItem extends FlowItemStack {

		public RuleFlowData DATA;

		public SelectionRulesDrawerItem(
			RuleFlowData data
		) {
			super(data.getIcon(), new Position());
			this.DATA = data;
			setDraggable(false);
		}

		@Override
		public void onSelectionChanged() {
			List<UUID> next = getChildrenRules().stream()
				.map(FlowData::getId)
				.collect(Collectors.toList());
			if (isSelected()) {
				next.add(DATA.getId());
			} else {
				next.remove(DATA.getId());
			}
			setChildrenRules(next);
		}
	}

	private class EditChildrenButton extends FlowPlusButton {

		private boolean open = false;

		public EditChildrenButton() {
			super(
				new Position(),
				new Size(FlowItemStack.ITEM_TOTAL_WIDTH, FlowItemStack.ITEM_TOTAL_HEIGHT),
				CONST.SELECTED
			);
			setDraggable(false);
		}

		@Override
		public void onClicked(int mx, int my, int button) {
			open = !open;
			SELECTION_RULES_DRAWER.setEnabled(open);
			SELECTION_RULES_DRAWER.setVisible(open);
		}
	}

	private class AddRuleButton extends FlowPlusButton {

		private final ItemStack[] items = {
			new ItemStack(Blocks.BEACON),
			new ItemStack(Blocks.STONE),
			new ItemStack(Blocks.SAND),
			new ItemStack(Blocks.SANDSTONE),
			new ItemStack(Blocks.TURTLE_EGG),
			new ItemStack(Blocks.DRAGON_EGG),
			new ItemStack(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE),
			new ItemStack(Blocks.CREEPER_HEAD),
		};

		public AddRuleButton() {
			super(
				new Position(),
				new Size(FlowItemStack.ITEM_TOTAL_WIDTH, FlowItemStack.ITEM_TOTAL_HEIGHT),
				CONST.SELECTED
			);
			setDraggable(false);
		}

		@Override
		public void onClicked(int mx, int my, int button) {
			//todo: remove debug item icons, or put more effort into random rule icons
			PacketHandler.INSTANCE.sendToServer(new ManagerCreateTileEntityRulePacketC2S(
				CONTROLLER.SCREEN.CONTAINER.windowId,
				CONTROLLER.SCREEN.CONTAINER.getSource().getPos(),
				"New tile entity rule",
				items[(int) (Math.random() * items.length)],
				new Position(0, 0)
			));
		}
	}
}
