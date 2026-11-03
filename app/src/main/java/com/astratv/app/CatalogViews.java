package com.astratv.app;

import android.content.Context;
import android.os.Parcelable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.astratv.app.coreui.*;
import java.util.*;

/**
 * Recycled TV rows and grids. Cards are built once per holder and rebound in place; lists update
 * through DiffUtil with stable ids, so a background sync never resets the focused card.
 */
final class CatalogViews {
    interface Host{
        void onItemFocused(String rowId,CatalogItem item,int index);
        void onItemClicked(String rowId,CatalogItem item);
        void onItemLongPressed(String rowId,CatalogItem item);
        /** Playback progress 0-100 for the resume bar; 0 hides it. */
        int progress(CatalogItem item);
        boolean favorite(CatalogItem item);
    }
    static final int POSTER=0,WIDE=1;
    static final DiffUtil.ItemCallback<CatalogItem> DIFF=new DiffUtil.ItemCallback<CatalogItem>(){
        @Override public boolean areItemsTheSame(@NonNull CatalogItem a,@NonNull CatalogItem b){return a.key().equals(b.key());}
        @Override public boolean areContentsTheSame(@NonNull CatalogItem a,@NonNull CatalogItem b){return a.signature().equals(b.signature());}
    };
    final Context context;final TvUi ui;final Host host;final RecyclerView.RecycledViewPool pool=new RecyclerView.RecycledViewPool();
    CatalogViews(Context context,TvUi ui,Host host){this.context=context;this.ui=ui;this.host=host;pool.setMaxRecycledViews(POSTER,40);pool.setMaxRecycledViews(WIDE,30);}

    int cardWidth(int style){return ui.dp(style==WIDE?224:139);}
    int artHeight(int style){return ui.dp(style==WIDE?124:184);}
    int rowHeight(int style){return artHeight(style)+ui.dp(62);}

    // ---- cards -----------------------------------------------------------------------------
    final class CardHolder extends RecyclerView.ViewHolder{
        final LinearLayout card;final Poster poster;final TextView badge,status,title,subtitle;final ProgressBar progress;final int style;
        CatalogItem item;String rowId="";int index;
        CardHolder(LinearLayout card,Poster poster,TextView badge,TextView status,ProgressBar progress,TextView title,TextView subtitle,int style){
            super(card);this.card=card;this.poster=poster;this.badge=badge;this.status=status;this.progress=progress;this.title=title;this.subtitle=subtitle;this.style=style;
        }
    }
    CardHolder createCard(int style,boolean grid){
        LinearLayout card=ui.column();card.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(6));
        RecyclerView.LayoutParams lp=new RecyclerView.LayoutParams(grid?-1:cardWidth(style),-2);if(!grid)lp.rightMargin=ui.dp(13);else lp.setMargins(ui.dp(4),0,ui.dp(9),ui.dp(14));card.setLayoutParams(lp);
        Poster poster=new Poster(ui,style==WIDE,18);FrameLayout art=poster.root;
        TextView badge=ui.badge("",0xcc0b0d12);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.START);bp.setMargins(ui.dp(6),ui.dp(6),0,0);art.addView(badge,bp);
        TextView status=ui.badge("",0xcc0b0d12);FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);sp.setMargins(0,ui.dp(6),ui.dp(6),0);art.addView(status,sp);
        ProgressBar progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);art.addView(progress,new FrameLayout.LayoutParams(-1,ui.dp(3),Gravity.BOTTOM));
        card.addView(art,new LinearLayout.LayoutParams(-1,artHeight(style)));ui.gap(card,8);
        TextView title=ui.bold("",13);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);ui.gap(card,4);
        TextView subtitle=ui.text("",10,TvUi.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);card.addView(subtitle);
        CardHolder h=new CardHolder(card,poster,badge,status,progress,title,subtitle,style);
        ui.focusCard(card,()->{if(h.item!=null)host.onItemFocused(h.rowId,h.item,h.getBindingAdapterPosition());});
        card.setOnClickListener(v->{if(h.item!=null)host.onItemClicked(h.rowId,h.item);});
        card.setOnLongClickListener(v->{if(h.item!=null)host.onItemLongPressed(h.rowId,h.item);return h.item!=null;});
        return h;
    }
    void bind(CardHolder h,CatalogItem c,String rowId,int index){
        h.item=c;h.rowId=rowId;h.index=index;Library.Title t=c.title;
        h.card.setTag(c.key());h.card.setContentDescription(t.name+", "+subtitle(c));
        h.title.setText(t.name);h.subtitle.setText(subtitle(c));
        String badge=badge(c);h.badge.setText(badge);h.badge.setVisibility(badge.isEmpty()?View.GONE:View.VISIBLE);
        String status=status(c);h.status.setText(status);h.status.setVisibility(status.isEmpty()?View.GONE:View.VISIBLE);
        h.status.setBackground(ui.shape(statusColor(c),0,8));
        int p=host.progress(c);h.progress.setProgress(p);h.progress.setVisibility(p>0?View.VISIBLE:View.GONE);
        h.poster.bind(t.name,CatalogItem.CHANNEL.equals(c.kind)||CatalogItem.EVENT.equals(c.kind)||CatalogItem.CATCHUP.equals(c.kind)?0:c.year(),t.poster.isEmpty()?t.background:t.poster);
    }
    /** Quality only when an authorized provider verified it; LIVE only when the schedule says so or the player validated the channel. */
    static String badge(CatalogItem c){
        if(CatalogItem.EVENT.equals(c.kind))return c.live()?"● LIVE":"";
        if(CatalogItem.CHANNEL.equals(c.kind)&&ChannelState.of(c,false,System.currentTimeMillis())==ChannelState.LIVE)return "● LIVE";
        if(c.verifiedHeight>0)return PlaybackPolicy.heightLabel(c.verifiedHeight);
        return "";
    }
    String status(CatalogItem c){
        if(c.handoff())return (CatalogItem.CHANNEL.equals(c.kind)&&host.favorite(c)?"★ ":"")+handoffLabel(c);
        if(CatalogItem.CHANNEL.equals(c.kind))return (host.favorite(c)?"★ ":"")+("ok".equals(c.health)?"Online":"failed".equals(c.health)?"Offline":"Unchecked");
        if(CatalogItem.EVENT.equals(c.kind))return c.live()?"":SportsSource.statusLabel(c);
        if(!c.availability.isEmpty())return availabilityBadge(c.availability());
        if(c.playable)return "▶ Play";
        return "";
    }
    /** Poster badge for titles whose availability a source provider reported. Only AVAILABLE says Play. */
    static String availabilityBadge(MovieAvailability a){
        switch(a){
            case AVAILABLE:return "▶ Play";
            case CHECKING:return "Checking";
            case LOGIN_REQUIRED:return "Login required";
            case UNSUPPORTED:return "Unsupported";
            case PROVIDER_ERROR:return "Provider error";
            default:return "Unavailable";
        }
    }
    /** Official-route entries say where they open and whether that costs anything; never "Online". */
    static String handoffLabel(CatalogItem c){
        String app=CatalogItem.YOUTUBE_HANDOFF.equals(c.playbackType)?"YouTube":OfficialAppHandoff.appName(c.playbackReference);
        return CatalogItem.SUBSCRIPTION.equals(c.accessType)?"Subscription · "+app:CatalogItem.FREE.equals(c.accessType)?"Free · "+app:app;
    }
    static int statusColor(CatalogItem c){
        if(c.handoff())return CatalogItem.SUBSCRIPTION.equals(c.accessType)?0xcc6a4a12:0xcc1f4f8a;
        if(CatalogItem.CHANNEL.equals(c.kind))return "ok".equals(c.health)?0xcc1f6b3a:"failed".equals(c.health)?0xcc7a2630:0xcc33363d;
        if(CatalogItem.EVENT.equals(c.kind))return 0xcc33363d;
        if(!c.availability.isEmpty()&&c.availability()!=MovieAvailability.AVAILABLE)return c.availability()==MovieAvailability.CHECKING?0xcc33363d:0xcc7a2630;
        return 0xcc1f4f8a;
    }
    static String subtitle(CatalogItem c){
        Library.Title t=c.title;
        if(CatalogItem.EVENT.equals(c.kind))return join(c.sport,time(c.startsAt),t.group.isEmpty()?"":t.group);
        if(CatalogItem.CHANNEL.equals(c.kind))return c.episodeLabel.isEmpty()?join(t.language,t.group):c.episodeLabel;
        if(!c.episodeLabel.isEmpty())return c.episodeLabel;
        String rating=c.rating>0&&!c.ratingSource.isEmpty()?String.format(Locale.US,"★ %.1f",c.rating):"";
        return join(c.year()>0?String.valueOf(c.year()):"",t.language,rating,c.audio.size()>1?c.audio.size()+" audio":"");
    }
    static String time(long at){
        if(at<=0)return "";
        java.time.ZonedDateTime z=java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault());java.time.LocalDate today=java.time.LocalDate.now();
        String day=z.toLocalDate().equals(today)?"Today":z.toLocalDate().equals(today.plusDays(1))?"Tomorrow":z.toLocalDate().equals(today.minusDays(1))?"Yesterday":z.getDayOfMonth()+" "+z.getMonth().getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH);
        return day+" "+String.format(Locale.US,"%02d:%02d",z.getHour(),z.getMinute());
    }
    static String join(String...parts){List<String> out=new ArrayList<>();for(String p:parts)if(p!=null&&!p.isEmpty())out.add(p);return String.join("  ·  ",out);}

    // ---- adapters ----------------------------------------------------------------------------
    final class PosterAdapter extends androidx.recyclerview.widget.ListAdapter<CatalogItem,CardHolder>{
        final String rowId;final int style;final boolean grid;
        PosterAdapter(String rowId,int style,boolean grid){super(DIFF);this.rowId=rowId;this.style=style;this.grid=grid;setHasStableIds(true);}
        @Override public long getItemId(int position){return CatalogItem.stableId(getItem(position).key());}
        @Override public int getItemViewType(int position){return style;}
        @NonNull @Override public CardHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){return createCard(type,grid);}
        @Override public void onBindViewHolder(@NonNull CardHolder h,int position){
            bind(h,getItem(position),rowId,position);
            if(style==POSTER&&position+1<getItemCount())Artwork.prefetch(context,getItem(position+1).title.poster);
        }
        @Override public void onViewRecycled(@NonNull CardHolder h){h.poster.recycle();h.item=null;}
        int indexOf(String key){List<CatalogItem> l=getCurrentList();for(int i=0;i<l.size();i++)if(l.get(i).key().equals(key))return i;return -1;}
    }
    /** One fixed view (grid header or footer) inside a ConcatAdapter. */
    static final class SingleViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private final View view;private final long id;private boolean shown=true;
        SingleViewAdapter(View view,long id){this.view=view;this.id=id;setHasStableIds(true);}
        void show(boolean value){if(value==shown)return;shown=value;if(value)notifyItemInserted(0);else notifyItemRemoved(0);}
        @Override public long getItemId(int position){return id;}
        @Override public int getItemCount(){return shown?1:0;}
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){
            ViewGroup old=(ViewGroup)view.getParent();if(old!=null)old.removeView(view);
            view.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RecyclerView.ViewHolder(view){};
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h,int position){}
    }
    static final class RowSpec{
        final String id,title;final int style;final boolean hideWhenEmpty;
        RowSpec(String id,String title,int style,boolean hideWhenEmpty){this.id=id;this.title=title;this.style=style;this.hideWhenEmpty=hideWhenEmpty;}
    }
    final class RowHolder extends RecyclerView.ViewHolder{
        final TextView title,note;final RecyclerView list;final ShimmerView shimmer;final TextView empty;PosterAdapter adapter;RowSpec spec;
        RowHolder(LinearLayout root,TextView title,TextView note,RecyclerView list,ShimmerView shimmer,TextView empty){super(root);this.title=title;this.note=note;this.list=list;this.shimmer=shimmer;this.empty=empty;}
    }
    /**
     * Vertical list of horizontal rows. Rows load independently; a row update re-submits only that
     * row's list. Optional header view (hero or page heading) sits at position 0.
     */
    final class RowsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
        private static final int HEADER=100,ROW=101;
        private final View header;private final List<RowSpec> all;private final List<RowSpec> visible=new ArrayList<>();
        private final Map<String,List<CatalogItem>> data=new HashMap<>();private final Map<String,String> notes=new HashMap<>();
        private final Map<String,Parcelable> scroll=new HashMap<>();
        private final Map<String,RowHolder> bound=new HashMap<>();
        String focusRow="",focusKey="";
        /** Set when returning to this screen (Back): the remembered card wins over Android's default focus. */
        boolean forceFocus;
        RowsAdapter(View header,List<RowSpec> rows){this.header=header;this.all=rows;visible.addAll(rows);setHasStableIds(true);}
        @Override public long getItemId(int position){return header!=null&&position==0?Long.MIN_VALUE:CatalogItem.stableId(spec(position).id);}
        @Override public int getItemCount(){return visible.size()+(header==null?0:1);}
        @Override public int getItemViewType(int position){return header!=null&&position==0?HEADER:ROW;}
        private RowSpec spec(int position){return visible.get(position-(header==null?0:1));}
        List<RowSpec> visibleRows(){return new ArrayList<>(visible);}
        List<RowSpec> specs(){return Collections.unmodifiableList(all);}
        List<CatalogItem> items(String rowId){List<CatalogItem> l=data.get(rowId);return l==null?Collections.emptyList():l;}
        boolean loaded(String rowId){return data.containsKey(rowId);}
        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent,int type){
            if(type==HEADER){ViewGroup old=(ViewGroup)header.getParent();if(old!=null)old.removeView(header);header.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RecyclerView.ViewHolder(header){};}
            LinearLayout root=ui.column();root.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));root.setPadding(0,0,0,ui.dp(18));
            LinearLayout line=new LinearLayout(context);line.setGravity(Gravity.CENTER_VERTICAL);
            TextView title=ui.bold("",20);line.addView(title);
            TextView note=ui.text("",11,TvUi.MUTED);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-2,-2);np.leftMargin=ui.dp(12);line.addView(note,np);
            root.addView(line);ui.gap(root,8);
            FrameLayout body=new FrameLayout(context);body.setClipChildren(false);
            RecyclerView list=new RecyclerView(context);list.setClipChildren(false);list.setClipToPadding(false);list.setPadding(ui.dp(4),ui.dp(6),ui.dp(40),ui.dp(8));
            list.setHorizontalScrollBarEnabled(false);list.setItemAnimator(null);list.setRecycledViewPool(pool);list.setHasFixedSize(true);
            LinearLayoutManager lm=new LinearLayoutManager(context,RecyclerView.HORIZONTAL,false);lm.setInitialPrefetchItemCount(4);list.setLayoutManager(lm);
            body.addView(list,new FrameLayout.LayoutParams(-1,-1));
            ShimmerView shimmer=new ShimmerView(context);body.addView(shimmer,new FrameLayout.LayoutParams(-1,ui.dp(128)));
            TextView empty=ui.text("",14,TvUi.MUTED);empty.setPadding(ui.dp(4),ui.dp(10),0,0);body.addView(empty,new FrameLayout.LayoutParams(-1,-2));
            root.addView(body,new LinearLayout.LayoutParams(-1,rowHeight(POSTER)));
            return new RowHolder(root,title,note,list,shimmer,empty);
        }
        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,int position){
            if(!(holder instanceof RowHolder))return;
            RowHolder h=(RowHolder)holder;RowSpec s=spec(position);
            if(h.spec!=null&&!h.spec.id.equals(s.id)){saveScroll(h);bound.remove(h.spec.id);}
            h.spec=s;bound.put(s.id,h);
            h.title.setText(s.title);
            ((LinearLayout.LayoutParams)((View)h.list.getParent()).getLayoutParams()).height=rowHeight(s.style);
            if(h.adapter==null||!h.adapter.rowId.equals(s.id)||h.adapter.style!=s.style){h.adapter=new PosterAdapter(s.id,s.style,false);h.list.setAdapter(h.adapter);}
            render(h);
            Parcelable state=scroll.get(s.id);if(state!=null&&h.list.getLayoutManager()!=null)h.list.getLayoutManager().onRestoreInstanceState(state);
        }
        @Override public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder){if(holder instanceof RowHolder){RowHolder h=(RowHolder)holder;saveScroll(h);if(h.spec!=null)bound.remove(h.spec.id);}}
        private void saveScroll(RowHolder h){if(h.spec!=null&&h.list.getLayoutManager()!=null)scroll.put(h.spec.id,h.list.getLayoutManager().onSaveInstanceState());}
        private void render(RowHolder h){
            String id=h.spec.id;List<CatalogItem> items=data.get(id);String note=notes.getOrDefault(id,"");
            h.note.setText(note);
            boolean loading=items==null,empty=items!=null&&items.isEmpty();
            h.shimmer.setVisibility(loading?View.VISIBLE:View.GONE);
            h.empty.setVisibility(empty?View.VISIBLE:View.GONE);h.empty.setText(empty?(note.isEmpty()?"Nothing here yet.":"Nothing here yet · "+note):"");
            h.list.setVisibility(loading||empty?View.INVISIBLE:View.VISIBLE);
            h.adapter.submitList(items==null?null:new ArrayList<>(items),()->restoreFocus(h));
        }
        private void restoreFocus(RowHolder h){
            if(h.spec==null||!h.spec.id.equals(focusRow)||focusKey.isEmpty())return;
            int i=h.adapter.indexOf(focusKey);if(i<0)return;
            RecyclerView.ViewHolder card=h.list.findViewHolderForAdapterPosition(i);
            if(card!=null){if(!card.itemView.hasFocus()&&wantsFocus())card.itemView.requestFocus();focusRow="";forceFocus=false;return;}
            h.list.scrollToPosition(i);h.list.post(()->{RecyclerView.ViewHolder c=h.list.findViewHolderForAdapterPosition(i);if(c!=null&&wantsFocus())c.itemView.requestFocus();focusRow="";forceFocus=false;});
        }
        /** Pull focus into a row only on return (Back) or when nothing else on screen holds focus. */
        private boolean wantsFocus(){if(forceFocus)return true;View f=((android.app.Activity)context).getCurrentFocus();return f==null||!f.isShown();}
        /** Updates one row in place. Empty rows marked hide-when-empty are removed from the list. */
        void setRow(String rowId,List<CatalogItem> items){
            data.put(rowId,items);
            RowSpec spec=null;for(RowSpec s:all)if(s.id.equals(rowId))spec=s;if(spec==null)return;
            int at=visible.indexOf(spec);
            if(items.isEmpty()&&spec.hideWhenEmpty){if(at>=0){visible.remove(at);notifyItemRemoved(at+(header==null?0:1));}return;}
            if(at<0){int insert=0;for(RowSpec s:all){if(s==spec)break;if(visible.contains(s))insert++;}visible.add(insert,spec);notifyItemInserted(insert+(header==null?0:1));return;}
            RowHolder h=bound.get(rowId);if(h!=null&&h.spec==spec)render(h);else notifyItemChanged(at+(header==null?0:1));
        }
        void setNote(String rowId,String note){
            notes.put(rowId,note);RowHolder h=bound.get(rowId);if(h!=null&&h.spec!=null&&h.spec.id.equals(rowId))h.note.setText(note);
        }
        Map<String,Parcelable> scrollStates(){for(RowHolder h:bound.values())saveScroll(h);return new HashMap<>(scroll);}
        void restoreScrollStates(Map<String,Parcelable> states){if(states!=null)scroll.putAll(states);}
    }
}
