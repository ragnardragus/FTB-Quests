package com.feed_the_beast.ftbquests.quest;

import com.feed_the_beast.ftbquests.events.ObjectCompletedEvent;
import com.feed_the_beast.ftbquests.net.MessageDisplayCompletionToast;
import com.feed_the_beast.ftbquests.util.NetUtils;
import com.feed_the_beast.ftbquests.util.OrderedCompoundNBT;
import com.feed_the_beast.mods.ftbguilibrary.config.ConfigGroup;
import com.feed_the_beast.mods.ftbguilibrary.config.ConfigString;
import com.feed_the_beast.mods.ftbguilibrary.icon.Icon;
import com.feed_the_beast.mods.ftbguilibrary.icon.IconAnimation;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author LatvianModder
 */
public final class Chapter extends QuestObject
{
	public final QuestFile file;
	public String filename;
	public final List<Quest> quests;
	public final List<String> subtitle;
	public boolean alwaysInvisible;
	public Chapter group;
	public QuestShape defaultQuestShape;
	public final List<ChapterImage> images;
	public int orderIndex;

	public Chapter(QuestFile f)
	{
		file = f;
		filename = "";
		quests = new ArrayList<>();
		subtitle = new ArrayList<>(0);
		alwaysInvisible = false;
		group = null;
		defaultQuestShape = QuestShape.DEFAULT;
		images = new ArrayList<>();
		orderIndex = 0;
	}

	@Override
	public QuestObjectType getObjectType()
	{
		return QuestObjectType.CHAPTER;
	}

	@Override
	public QuestFile getQuestFile()
	{
		return file;
	}

	@Override
	public Chapter getQuestChapter()
	{
		return this;
	}

	@Override
	public void writeData(CompoundNBT nbt)
	{
		nbt.putString("filename", filename);
		nbt.putInt("order_index", orderIndex);
		super.writeData(nbt);

		if (!subtitle.isEmpty())
		{
			ListNBT list = new ListNBT();

			for (String v : subtitle)
			{
				list.add(StringNBT.valueOf(v));
			}

			nbt.put("subtitle", list);
		}

		if (alwaysInvisible)
		{
			nbt.putBoolean("always_invisible", true);
		}

		if (group != null && !group.invalid)
		{
			nbt.putInt("group", group.id);
		}

		if (defaultQuestShape != QuestShape.DEFAULT)
		{
			nbt.putString("default_quest_shape", defaultQuestShape.id);
		}

		if (!images.isEmpty())
		{
			ListNBT list = new ListNBT();

			for (ChapterImage image : images)
			{
				CompoundNBT nbt1 = new OrderedCompoundNBT();
				image.writeData(nbt1);
				list.add(nbt1);
			}

			nbt.put("images", list);
		}
	}

	@Override
	public void readData(CompoundNBT nbt)
	{
		filename = nbt.getString("filename");
		orderIndex = nbt.getInt("order_index");
		super.readData(nbt);
		subtitle.clear();

		ListNBT subtitleNBT = nbt.getList("subtitle", Constants.NBT.TAG_STRING);

		for (int i = 0; i < subtitleNBT.size(); i++)
		{
			subtitle.add(subtitleNBT.getString(i));
		}

		alwaysInvisible = nbt.getBoolean("always_invisible");
		group = file.getChapter(nbt.getInt("group"));
		defaultQuestShape = QuestShape.NAME_MAP.get(nbt.getString("default_quest_shape"));

		ListNBT imgs = nbt.getList("images", Constants.NBT.TAG_COMPOUND);

		images.clear();

		for (int i = 0; i < imgs.size(); i++)
		{
			ChapterImage image = new ChapterImage(this);
			image.readData(imgs.getCompound(i));
			images.add(image);
		}
	}

	@Override
	public void writeNetData(PacketBuffer buffer)
	{
		super.writeNetData(buffer);
		NetUtils.writeStrings(buffer, subtitle);
		buffer.writeBoolean(alwaysInvisible);
		buffer.writeInt(group == null || group.invalid ? 0 : group.id);
		QuestShape.NAME_MAP.write(buffer, defaultQuestShape);
		NetUtils.write(buffer, images, (d, img) -> img.writeNetData(d));
	}

	@Override
	public void readNetData(PacketBuffer buffer)
	{
		super.readNetData(buffer);
		NetUtils.readStrings(buffer, subtitle);
		alwaysInvisible = buffer.readBoolean();
		group = file.getChapter(buffer.readInt());
		defaultQuestShape = QuestShape.NAME_MAP.read(buffer);
		NetUtils.read(buffer, images, d -> {
			ChapterImage image = new ChapterImage(this);
			image.readNetData(d);
			return image;
		});
	}

	public int getIndex()
	{
		return file.chapters.indexOf(this);
	}

	@Override
	public int getRelativeProgressFromChildren(PlayerData data)
	{
		if (alwaysInvisible)
		{
			return 100;
		}

		List<Chapter> children = getChildren();

		if (quests.isEmpty() && children.isEmpty())
		{
			return 100;
		}

		int progress = 0;
		int count = 0;

		for (Quest quest : quests)
		{
			progress += data.getRelativeProgress(quest);
			count++;
		}

		for (Chapter chapter : getChildren())
		{
			progress += chapter.getRelativeProgressFromChildren(data);
			count++;
		}

		if (count <= 0)
		{
			return 100;
		}

		return getRelativeProgressFromChildren(progress, count);
	}

	@Override
	public void onCompleted(PlayerData data, List<ServerPlayerEntity> onlineMembers, List<ServerPlayerEntity> notifiedPlayers)
	{
		super.onCompleted(data, onlineMembers, notifiedPlayers);
		MinecraftForge.EVENT_BUS.post(new ObjectCompletedEvent.ChapterEvent(data, this, onlineMembers, notifiedPlayers));

		if (!disableToast)
		{
			for (ServerPlayerEntity player : notifiedPlayers)
			{
				new MessageDisplayCompletionToast(id).sendTo(player);
			}
		}

		if (data.isComplete(file))
		{
			file.onCompleted(data, onlineMembers, notifiedPlayers);
		}
	}

	@Override
	public void changeProgress(PlayerData data, ChangeProgress type)
	{
		for (Quest quest : quests)
		{
			quest.changeProgress(data, type);
		}

		for (Chapter chapter : getChildren())
		{
			chapter.changeProgress(data, type);
		}
	}

	@Override
	public Icon getAltIcon()
	{
		List<Icon> list = new ArrayList<>();

		for (Quest quest : quests)
		{
			list.add(quest.getIcon());
		}

		for (Chapter child : getChildren())
		{
			list.add(child.getIcon());
		}

		return IconAnimation.fromList(list, false);
	}

	@Override
	public String getAltTitle()
	{
		return I18n.format("ftbquests.unnamed");
	}

	@Override
	public void deleteSelf()
	{
		super.deleteSelf();
		file.chapters.remove(this);
	}

	@Override
	public void deleteChildren()
	{
		for (Quest quest : quests)
		{
			quest.deleteChildren();
			quest.invalid = true;
		}

		quests.clear();
	}

	@Override
	public void onCreated()
	{
		file.chapters.add(this);

		if (!quests.isEmpty())
		{
			List<Quest> l = new ArrayList<>(quests);
			quests.clear();
			for (Quest quest : l)
			{
				quest.onCreated();
			}
		}
	}

	@Override
	public String getPath()
	{
		return "chapters/" + filename;
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void getConfig(ConfigGroup config)
	{
		super.getConfig(config);
		config.addList("subtitle", subtitle, new ConfigString(null), "");
		config.addBool("always_invisible", alwaysInvisible, v -> alwaysInvisible = v, false);

		//Predicate<QuestObjectBase> predicate = object -> object == null || (object instanceof Chapter && object != this && ((Chapter) object).group == null);
		//config.add("group", new ConfigQuestObject<>(predicate), group, v -> group = v, null);

		config.addEnum("default_quest_shape", defaultQuestShape, v -> defaultQuestShape = v, QuestShape.NAME_MAP);
	}

	@Override
	public boolean isVisible(PlayerData data)
	{
		if (alwaysInvisible)
		{
			return false;
		}

		for (Quest quest : quests)
		{
			if (quest.isVisible(data))
			{
				return true;
			}
		}

		for (Chapter child : getChildren())
		{
			if (child.isVisible(data))
			{
				return true;
			}
		}

		return false;
	}

	@Override
	public void clearCachedData()
	{
		super.clearCachedData();

		for (Quest quest : quests)
		{
			quest.clearCachedData();
		}

		for (Chapter chapter : getChildren())
		{
			chapter.clearCachedData();
		}
	}

	public boolean hasChildren()
	{
		if (group != null)
		{
			return false;
		}

		for (Chapter chapter : file.chapters)
		{
			if (chapter.group == this)
			{
				return true;
			}
		}

		return false;
	}

	public List<Chapter> getChildren()
	{
		List<Chapter> list = Collections.emptyList();

		if (group != null)
		{
			return list;
		}

		for (Chapter chapter : file.chapters)
		{
			if (chapter.group == this)
			{
				if (list.isEmpty())
				{
					list = new ArrayList<>(3);
				}

				list.add(chapter);
			}
		}

		return list;
	}

	@Override
	public boolean verifyDependenciesInternal(QuestObject original, boolean firstLoop)
	{
		if (this == original && !firstLoop)
		{
			return false;
		}

		for (Quest quest : quests)
		{
			if (!quest.verifyDependenciesInternal(original, false))
			{
				return false;
			}
		}

		return true;
	}

	public boolean hasGroup()
	{
		return group != null && !group.invalid;
	}

	public QuestShape getDefaultQuestShape()
	{
		return defaultQuestShape == QuestShape.DEFAULT ? file.getDefaultQuestShape() : defaultQuestShape;
	}
}