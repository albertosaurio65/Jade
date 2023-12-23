package snownee.jade.addon.universal;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ViewGroup;

public class ItemCollector<T> {
	public static final int MAX_SIZE = 54;
	public static final ItemCollector<?> EMPTY = new ItemCollector<>(null);
	private static final Predicate<ItemStack> NON_EMPTY = stack -> {
		if (stack.isEmpty()) {
			return false;
		}
		CompoundTag tag = stack.getTag();
		if (tag != null && tag.contains("CustomModelData")) {
			for (String key : stack.getTag().getAllKeys()) {
				if (key.toLowerCase(Locale.ENGLISH).endsWith("clear") && stack.getTag().getBoolean(key)) {
					return false;
				}
			}
		}
		return true;
	};
	private final Object2IntLinkedOpenHashMap<ItemDefinition> items = new Object2IntLinkedOpenHashMap<>();
	private final ItemIterator<T> iterator;
	public long version;
	public long lastTimeFinished;
	public List<ViewGroup<ItemStack>> mergedResult;

	public ItemCollector(ItemIterator<T> iterator) {
		this.iterator = iterator;
	}

	public List<ViewGroup<ItemStack>> update(Accessor<?> target, long gameTime) {
		if (iterator == null) {
			return null;
		}
		T container = iterator.find(target);
		if (container == null) {
			return null;
		}
		long currentVersion = iterator.getVersion(container);
		if (mergedResult != null && iterator.isFinished()) {
			if (version == currentVersion) {
				return mergedResult; // content not changed
			}
			if (lastTimeFinished + 5 > gameTime) {
				return mergedResult; // avoid update too frequently
			}
			iterator.reset();
		}
		AtomicInteger count = new AtomicInteger();
		iterator.populate(container).forEach(stack -> {
			count.incrementAndGet();
			if (NON_EMPTY.test(stack)) {
				ItemDefinition def = new ItemDefinition(stack);
				items.addTo(def, stack.getCount());
			}
		});
		iterator.afterPopulate(count.get());
		if (mergedResult != null && !iterator.isFinished()) {
			updateCollectingProgress(mergedResult.get(0));
			return mergedResult;
		}
		List<ItemStack> partialResult = items.object2IntEntrySet().stream().limit(54).map(entry -> {
			ItemDefinition def = entry.getKey();
			return def.toStack(entry.getIntValue());
		}).toList();
		List<ViewGroup<ItemStack>> groups = List.of(updateCollectingProgress(new ViewGroup<>(partialResult)));
		if (iterator.isFinished()) {
			mergedResult = groups;
			version = currentVersion;
			lastTimeFinished = gameTime;
			items.clear();
		}
		return groups;
	}

	protected ViewGroup<ItemStack> updateCollectingProgress(ViewGroup<ItemStack> group) {
		float progress = iterator.getCollectingProgress();
		CompoundTag data = group.getExtraData();
		if (Float.isNaN(progress)) {
			progress = 0;
		}
		if (progress >= 1) {
			data.remove("Collecting");
		} else {
			data.putFloat("Collecting", progress);
		}
		return group;
	}

	public record ItemDefinition(Item item, @Nullable CompoundTag tag) {
		ItemDefinition(ItemStack stack) {
			this(stack.getItem(), stack.getTag());
		}

		public ItemStack toStack(int count) {
			ItemStack stack = new ItemStack(item);
			stack.setCount(count);
			stack.setTag(tag);
			return stack;
		}
	}
}
