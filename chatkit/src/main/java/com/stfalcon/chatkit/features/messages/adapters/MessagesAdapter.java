package com.stfalcon.chatkit.features.messages.adapters;

import android.support.annotation.LayoutRes;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import com.stfalcon.chatkit.R;
import com.stfalcon.chatkit.commons.adapter.ViewHolder;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.features.messages.adapters.holders.DefaultDateHeaderViewHolder;
import com.stfalcon.chatkit.features.messages.adapters.holders.DefaultIncomingMessageViewHolder;
import com.stfalcon.chatkit.features.messages.adapters.holders.DefaultOutcomingMessageViewHolder;
import com.stfalcon.chatkit.features.messages.adapters.holders.MessageViewHolder;
import com.stfalcon.chatkit.features.utils.DatesUtils;
import com.stfalcon.chatkit.features.utils.RecyclerScrollMoreListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/*
 * Created by troy379 on 09.12.16.
 */
public class MessagesAdapter<MESSAGE extends IMessage>
        extends RecyclerView.Adapter<ViewHolder>
        implements RecyclerScrollMoreListener.OnLoadMoreListener {

    private static final int VIEW_TYPE_INCOMING_MESSAGE = 0x00;
    private static final int VIEW_TYPE_OUTCOMING_MESSAGE = 0x01;
    private static final int VIEW_TYPE_DATE_HEADER = 0x02;

    private HoldersConfig holders;
    private String senderId;
    private List<Wrapper> items;

    private int selectedItemsCount;
    private boolean isSelectMode;
    private SelectionListener selectionListener;

    private OnLoadMoreListener loadMoreListener;
    private OnClickListener<MESSAGE> onClickListener;
    private OnLongClickListener<MESSAGE> onLongClickListener;
    private MessageViewHolder.ImageLoader imageLoader;

    public MessagesAdapter(String senderId) {
        this(senderId, null);
    }

    public MessagesAdapter(String senderId, MessageViewHolder.ImageLoader imageLoader) {
        this(senderId, new HoldersConfig(), imageLoader);
    }

    public MessagesAdapter(String senderId, HoldersConfig holders,
                           MessageViewHolder.ImageLoader imageLoader) {
        this.senderId = senderId;
        this.holders = holders;
        this.imageLoader = imageLoader;
        this.items = new ArrayList<>();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_TYPE_INCOMING_MESSAGE:
                return ViewHolder.getHolder(parent, holders.incomingLayout, holders.incomingHolder);
            case VIEW_TYPE_OUTCOMING_MESSAGE:
                return ViewHolder.getHolder(parent, holders.outcomingLayout, holders.outcomingHolder);
            case VIEW_TYPE_DATE_HEADER:
                return ViewHolder.getHolder(parent, holders.dateHeaderLayout, holders.dateHeaderHolder);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        Wrapper wrapper = items.get(position);

        if (wrapper.item instanceof IMessage) {
            ((MessageViewHolder) holder).setSelected(wrapper.isSelected);
            ((MessageViewHolder) holder).setImageLoader(this.imageLoader);
            holder.itemView.setOnLongClickListener(getMessageLongClickListener(wrapper));
            holder.itemView.setOnClickListener(getMessageClickListener(wrapper));
        }

        holder.onBind(wrapper.item);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        Wrapper wrapper = items.get(position);
        if (wrapper.item instanceof IMessage) {
            IMessage message = (IMessage) wrapper.item;
            if (message.getUser().getId().contentEquals(senderId)) {
                return VIEW_TYPE_OUTCOMING_MESSAGE;
            } else {
                return VIEW_TYPE_INCOMING_MESSAGE;
            }
        } else {
            return VIEW_TYPE_DATE_HEADER;
        }
    }

    public void onLoadMore(int page, int total) {
        if (loadMoreListener != null) {
            loadMoreListener.onLoadMore(page, total);
        }
    }

    /*
        * PUBLIC METHODS
        * */
    public void add(MESSAGE message) {
        boolean isNewMessageToday = !isPreviousSameDate(0, message.getCreatedAt());
        if (isNewMessageToday) {
            items.add(0, new Wrapper<>(message.getCreatedAt()));
        }
        Wrapper<MESSAGE> element = new Wrapper<>(message);
        items.add(0, element);
        notifyItemRangeInserted(0, isNewMessageToday ? 2 : 1);
    }

    /**
     * Method for adding history.
     *
     * @param messages messages from history.
     * @param reverse  {@code true} if need to reverse messages before adding.
     */
    public void add(ArrayList<MESSAGE> messages, boolean reverse) {
        if (reverse) Collections.reverse(messages);

        if (!items.isEmpty()) {
            int lastItemPosition = items.size() - 1;
            Date lastItem = (Date) items.get(lastItemPosition).item;
            if (DatesUtils.isSameDay(messages.get(0).getCreatedAt(), lastItem)) {
                items.remove(lastItemPosition);
                notifyItemRemoved(lastItemPosition);
            }
        }

        int oldSize = items.size();
        generateDateHeaders(messages);
        notifyItemRangeInserted(oldSize, items.size() - oldSize);
    }

    public void update(String oldId, MESSAGE newMessage) {
        int position = getMessagePositionById(oldId);
        Wrapper<MESSAGE> element = new Wrapper<>(newMessage);
        items.set(position, element);
        notifyItemChanged(position);
    }

    public void deleteByIds(String[] ids) {
        for (String id : ids) {
            int index = getMessagePositionById(id);
            items.remove(index);
            notifyItemRemoved(index);
        }
        recountDateHeaders();
    }

    public void clear() {
        items.clear();
    }

    public void enableSelectionMode(SelectionListener selectionListener) {
        if (selectionListener == null) {
            throw new IllegalArgumentException("SelectionListener must not be null. Use `disableSelectionMode()` if you want tp disable selection mode");
        } else {
            this.selectionListener = selectionListener;
        }
    }

    public void disableSelectionMode() {
        this.selectionListener = null;
        unselectAllItems();
    }

    @SuppressWarnings("unchecked")
    public ArrayList<MESSAGE> getSelectedMessages() {
        ArrayList<MESSAGE> selectedMessages = new ArrayList<>();
        for (Wrapper wrapper : items) {
            if (wrapper.item instanceof IMessage && wrapper.isSelected) {
                selectedMessages.add((MESSAGE) wrapper.item);
            }
        }
        return selectedMessages;
    }

    public void unselectAllItems() {
        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.isSelected) {
                wrapper.isSelected = false;
                notifyItemChanged(i);
            }
        }
        isSelectMode = false;
        selectedItemsCount = 0;
        notifySelectionChanged();
    }

    public void setOnClickListener(OnClickListener<MESSAGE> onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setOnLongClickListener(OnLongClickListener<MESSAGE> onLongClickListener) {
        this.onLongClickListener = onLongClickListener;
    }

    public void setLoadMoreListener(OnLoadMoreListener loadMoreListener) {
        this.loadMoreListener = loadMoreListener;
    }

    /*
        * PRIVATE METHODS
        * */
    private void recountDateHeaders() {
        ArrayList<Integer> indicesToDelete = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.item instanceof Date) {
                if (i == 0) {
                    indicesToDelete.add(i);
                } else {
                    if (items.get(i - 1).item instanceof Date) {
                        indicesToDelete.add(i);
                    }
                }
            }
        }

        Collections.reverse(indicesToDelete);
        for (int i : indicesToDelete) {
            items.remove(i);
            notifyItemRemoved(i);
        }
    }

    private void generateDateHeaders(ArrayList<MESSAGE> messages) {
        for (int i = 0; i < messages.size(); i++) {
            MESSAGE message = messages.get(i);
            this.items.add(new Wrapper<>(message));
            if (messages.size() > i + 1) {
                MESSAGE nextMessage = messages.get(i + 1);
                if (!DatesUtils.isSameDay(message.getCreatedAt(), nextMessage.getCreatedAt())) {
                    this.items.add(new Wrapper<>(message.getCreatedAt()));
                }
            } else {
                this.items.add(new Wrapper<>(message.getCreatedAt()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private int getMessagePositionById(String id) {
        for (int i = 0; i < items.size(); i++) {
            Wrapper wrapper = items.get(i);
            if (wrapper.item instanceof IMessage) {
                MESSAGE message = (MESSAGE) wrapper.item;
                if (message.getId().contentEquals(id)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private boolean isPreviousSameDate(int position, Date dateToCompare) {
        if (items.size() <= position) return false;
        if (items.get(position).item instanceof IMessage) {
            Date previousPositionDate = ((MESSAGE) items.get(position).item).getCreatedAt();
            return DatesUtils.isSameDay(dateToCompare, previousPositionDate);
        } else return false;
    }

    @SuppressWarnings("unchecked")
    private boolean isPreviousSameAuthor(String id, int position) {
        int prevPosition = position + 1;
        if (items.size() <= prevPosition) return false;

        if (items.get(prevPosition).item instanceof IMessage) {
            return ((MESSAGE) items.get(prevPosition).item).getUser().getId().contentEquals(id);
        } else return false;
    }

    private void incrementSelectedItemsCount() {
        selectedItemsCount++;
        notifySelectionChanged();
    }

    private void decrementSelectedItemsCount() {
        selectedItemsCount--;
        isSelectMode = selectedItemsCount > 0;

        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged(selectedItemsCount);
        }
    }

    private void notifyMessageClicked(MESSAGE message) {
        if (onClickListener != null) {
            onClickListener.onMessageClick(message);
        }
    }

    private void notifyMessageLongClicked(MESSAGE message) {
        if (onLongClickListener != null) {
            onLongClickListener.onMessageLongClick(message);
        }
    }

    private View.OnClickListener getMessageClickListener(final Wrapper<MESSAGE> wrapper) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (selectionListener != null && isSelectMode) {
                    wrapper.isSelected = !wrapper.isSelected;

                    if (wrapper.isSelected) incrementSelectedItemsCount();
                    else decrementSelectedItemsCount();

                    MESSAGE message = (wrapper.item);
                    notifyItemChanged(getMessagePositionById(message.getId()));
                } else {
                    notifyMessageClicked(wrapper.item);
                }
            }
        };
    }

    private View.OnLongClickListener getMessageLongClickListener(final Wrapper<MESSAGE> wrapper) {
        return new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (selectionListener == null) {
                    notifyMessageLongClicked(wrapper.item);
                    return true;
                } else {
                    isSelectMode = true;
                    view.callOnClick();
                    return true;
                }
            }
        };
    }

    /*
    * HOLDERS CONFIG
    * */
    public static class HoldersConfig {

        private Class<? extends MessageViewHolder<? extends IMessage>> incomingHolder;
        private @LayoutRes int incomingLayout;

        private Class<? extends MessageViewHolder<? extends IMessage>> outcomingHolder;
        private @LayoutRes int outcomingLayout;

        private Class<? extends ViewHolder<Date>> dateHeaderHolder;
        private @LayoutRes int dateHeaderLayout;

        public HoldersConfig() {
            this.incomingHolder = DefaultIncomingMessageViewHolder.class;
            this.incomingLayout = R.layout.item_incoming_message;

            this.outcomingHolder = DefaultOutcomingMessageViewHolder.class;
            this.outcomingLayout = R.layout.item_outcoming_message;

            this.dateHeaderHolder = DefaultDateHeaderViewHolder.class;
            this.dateHeaderLayout = R.layout.item_date_header;
        }

        public void setIncoming(Class<? extends MessageViewHolder<? extends IMessage>> holder, @LayoutRes int layout) {
            this.incomingHolder = holder;
            this.incomingLayout = layout;
        }

        public void setOutcoming(Class<? extends MessageViewHolder<? extends IMessage>> holder, @LayoutRes int layout) {
            this.outcomingHolder = holder;
            this.outcomingLayout = layout;
        }

        public void setDateHeader(Class<? extends ViewHolder<Date>> holder, @LayoutRes int layout) {
            this.dateHeaderHolder = holder;
            this.dateHeaderLayout = layout;
        }
    }

    /*
    * WRAPPER
    * */
    private class Wrapper<DATA> {
        private DATA item;
        boolean isSelected;

        Wrapper(DATA item) {
            this.item = item;
        }
    }

    /*
    * LISTENERS
    * */
    public interface OnLoadMoreListener {
        void onLoadMore(int page, int totalItemsCount);
    }

    public interface SelectionListener {
        void onSelectionChanged(int count);
    }

    public interface OnClickListener<MESSAGE extends IMessage> {
        void onMessageClick(MESSAGE message);
    }

    public interface OnLongClickListener<MESSAGE extends IMessage> {
        void onMessageLongClick(MESSAGE message);
    }
}
