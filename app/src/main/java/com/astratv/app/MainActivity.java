package com.astratv.app;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.*;
import java.io.*;
import java.util.*;
import com.astratv.app.coreui.*;

/**
 * Native TV shell: six catalog tabs fed by the local catalog store (rendered immediately, refreshed
 * in the background), plus native details, episodes, source selection, settings and VPN.
 */
@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity implements CatalogViews.Host {
    static final String[] TABS={"Home","All Movies","HD Movies","TV Shows","Live TV","Sports Live"};
    private static final int ROW_LIMIT=40;
    private static final Set<String> UTILITY=new HashSet<>(Arrays.asList("Search","Watchlist","Settings","Add-ons","Sync","Research"));
    private TvUi ui;
    private ProviderRepository repository;
    private CatalogRepository catalog;
    private CatalogViews views;
    private android.content.SharedPreferences prefs;
    private FrameLayout frame;
    private LinearLayout content,body;
    private ScrollView scroll;
    private RecyclerView list;
    private ImageView backdrop;
    private View heroShade,heroFade;
    private TextView heroTitle,heroMeta,heroDescription,heroProvider,heroWatch,heroPlay;
    private LinearLayout heroChips;
    private View filterRail;
    private BrowseFilter draftFilter;
    private Library.Title featured,detailTitle;
    private CatalogItem featuredItem,detailItem,pendingHero,tmdbShow;
    private String screen="Home",tab="Home",focusKey="",refocusTag="";
    private int generation=0;
    private boolean heroChosen=false;
    private CatalogViews.RowsAdapter rows;
    private Grid grid;
    private final FocusAnchor anchor=new FocusAnchor();
    private final Map<String,List<CatalogItem>> rowCache=new HashMap<>();
    private final Map<String,GridMemory> gridMemory=new HashMap<>();
    private final Map<String,TextView> syncLines=new HashMap<>();
    private final Map<String,ProviderRepository.Catalog> catalogs=new LinkedHashMap<>();
    private final Map<String,ProviderRepository.Catalog> browsePages=new HashMap<>();
    private final Deque<Nav> history=new ArrayDeque<>();
    private int sourcePage=1,episodePage=1,episodeSeason=-1;
    private Library.Title episodeSeries;
    private String searchQuery="";
    private Runnable searchTask;
    private static final class Nav{String screen,query,personId,personName;int page,season,epPage,y;Library.Title title,series;CatalogItem item;}
    private static final class GridMemory{final String key;final Map<Integer,List<CatalogItem>> pages;final int page;final boolean more;GridMemory(String key,Map<Integer,List<CatalogItem>> pages,int page,boolean more){this.key=key;this.pages=pages;this.page=page;this.more=more;}}
    private final class Grid{
        final String name;final int style;final int cols;DiscoverQuery query;boolean local;Set<String> watches=new HashSet<>();
        final Map<Integer,List<CatalogItem>> pages=new TreeMap<>();int page;boolean loading,more=true;
        CatalogViews.PosterAdapter adapter;TextView status,footer;String focusKey="";GridLayoutManager layout;
        Grid(String name,int style,int cols){this.name=name;this.style=style;this.cols=cols;}
    }
    private Nav capture(){
        Nav n=new Nav();n.screen=screen;n.query=searchQuery;n.page=sourcePage;n.title=detailTitle;n.series=episodeSeries;n.season=episodeSeason;n.epPage=episodePage;n.y=scroll==null?0:scroll.getScrollY();
        n.item=screen.equals("TV Episodes")?tmdbShow:detailItem;return n;
    }
    private void back(){
        if(filterRail!=null){hideFilters();return;}
        if(history.isEmpty()){openTab(isTab(screen)?"Home":tab,true);return;}
        Nav n=history.pop();searchQuery=n.query;
        switch(n.screen){
            case "Details":detailItem=n.item;showDetails(n.title);break;
            case "Person":showPerson(n.personId,n.personName,false);break;
            case "Episodes":openEpisodes(n.series,n.season,n.epPage,false);break;
            case "TV Episodes":showTmdbEpisodes(n.item,n.season,false);break;
            case "Event":showEvent(n.item);break;
            case "Search":showSearch();break;
            case "Watchlist":showWatchlist();break;
            case "Add-ons":showAddons();break;
            case "Settings":showSettings();break;
            case "Sync":showSyncStatus();break;
            case "TV Browse":showTvBrowse(true);break;
            default:if(n.screen.startsWith("Provider:"))showProvider(addonFor(n.screen.substring(9)),n.page);else openTab(isTab(n.screen)?n.screen:"Home",true);
        }
        if(scroll!=null&&!screen.equals("Episodes")&&!screen.equals("TV Episodes"))scroll.post(()->{if(scroll!=null)scroll.scrollTo(0,n.y);});
    }
    private void openDetails(Library.Title t){saveTabState();history.push(capture());detailItem=null;showDetails(t);}
    private void openProvider(Library.Addon a){history.push(capture());showProvider(a,1);}
    private void openItem(CatalogItem c){
        saveTabState();history.push(capture());
        if(CatalogItem.EVENT.equals(c.kind))showEvent(c);else{detailItem=c;showDetails(c.title);}
    }
    private final Map<String,String> states=new HashMap<>();
    private final Map<String,TextView> addonStatus=new HashMap<>(),addonCount=new HashMap<>();
    private final Set<String> loading=new HashSet<>();
    private final Set<String> enriching=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private ResearchLog research;
    private MetadataRepository metadata;
    private TrailerEngine trailers;
    private FrameLayout trailerHost;
    private GlassPanel topNavigation;
    private VpnController.Listener vpnListener;
    private final CatalogRepository.Listener catalogListener=this::onRowsChanged;
    private final Runnable heroUpdate=()->{if(pendingHero!=null&&screen.equals("Home"))setHero(pendingHero);};

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new TvUi(this);prefs=getSharedPreferences("astra",0);research=new ResearchLog(prefs);repository=new ProviderRepository(this);metadata=new MetadataRepository(this);
        trailers=new TrailerEngine(this,metadata,repository.io);catalog=CatalogRepository.get(this);views=new CatalogViews(this,ui,this);
        catalog.background(this::migrateLegacy);
        catalog.background(()->{
            Map<String,ProviderRepository.Catalog> stored=new LinkedHashMap<>();
            for(Library.Addon a:Library.addons()){List<Library.Title> cached=repository.cached(a.id);if(!cached.isEmpty())stored.put(a.id,new ProviderRepository.Catalog(cached,"Saved catalog",true));}
            runOnUiThread(()->{for(Map.Entry<String,ProviderRepository.Catalog> e:stored.entrySet())catalogs.putIfAbsent(e.getKey(),e.getValue());});
        });
        showTab("Home");
    }
    @Override protected void onStart(){
        super.onStart();catalog.addListener(catalogListener);catalog.onForeground();
        catalog.touched(CatalogRepository.HOME_CONTINUE,CatalogRepository.TV_CONTINUE);
    }
    @Override protected void onStop(){saveTabState();catalog.removeListener(catalogListener);catalog.onBackground();super.onStop();}

    // ---- frame & navigation -------------------------------------------------------------------
    static boolean isTab(String name){for(String t:TABS)if(t.equals(name))return true;return false;}
    private void frame(String name,boolean cinematic){
        if(vpnListener!=null){VpnController.get(this).removeListener(vpnListener);vpnListener=null;}
        generation++;screen=name;addonStatus.clear();addonCount.clear();syncLines.clear();heroTitle=null;heroPlay=null;heroWatch=null;filterRail=null;rows=null;grid=null;list=null;scroll=null;
        main.removeCallbacks(heroUpdate);if(searchTask!=null)main.removeCallbacks(searchTask);if(trailers!=null)trailers.cancel();
        frame=new FrameLayout(this);frame.setBackgroundColor(TvUi.BG);frame.setClipChildren(false);
        if(cinematic){
            backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);frame.addView(backdrop,new FrameLayout.LayoutParams(-1,ui.dp(540),Gravity.TOP));
            trailerHost=new FrameLayout(this);frame.addView(trailerHost,new FrameLayout.LayoutParams(-1,ui.dp(540),Gravity.TOP));if(trailers!=null)trailers.attach(trailerHost);
            heroShade=new View(this);tintHero(TvUi.BG);frame.addView(heroShade,new FrameLayout.LayoutParams(-1,ui.dp(460)));
            heroFade=new View(this);heroFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x0008090c,0x6608090c,TvUi.BG}));frame.addView(heroFade,new FrameLayout.LayoutParams(-1,ui.dp(460)));
        }else{backdrop=null;heroShade=null;heroFade=null;trailerHost=null;if(trailers!=null)trailers.attach(null);}
        LinearLayout layout=new LinearLayout(this);layout.setClipChildren(false);frame.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        content=ui.column();content.setPadding(ui.dp(42),0,ui.dp(42),0);layout.addView(content,new LinearLayout.LayoutParams(-1,-1));
    }
    private void finishFrame(View animated){
        nav();setContentView(frame);
        if(!TvTheme.reduced(this)){animated.setAlpha(0f);animated.setTranslationY(ui.dp(8));animated.animate().alpha(1).translationY(0).setDuration(200).start();}
        if(!refocusTag.isEmpty()){String tag=refocusTag;refocusTag="";frame.post(()->{View v=findTagged(frame,tag);if(v!=null)v.requestFocus();});}
    }
    private void shell(String name,boolean cinematic){
        frame(name,cinematic);
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setVerticalScrollBarEnabled(false);body=ui.column();body.setPadding(ui.dp(8),ui.dp(96),ui.dp(8),ui.dp(40));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(topNavigation!=null)topNavigation.setElevation(ui.dp(y>16?12:0));if(backdrop!=null&&topNavigation!=null)topNavigation.artwork(backdrop.getDrawable());});
        finishFrame(body);
    }
    private RecyclerView shellList(String name,boolean cinematic){
        frame(name,cinematic);
        list=new RecyclerView(this);list.setClipChildren(false);list.setClipToPadding(false);list.setPadding(ui.dp(8),ui.dp(84),ui.dp(8),ui.dp(40));list.setItemAnimator(null);list.setVerticalScrollBarEnabled(false);
        content.addView(list,new LinearLayout.LayoutParams(-1,-1));
        list.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView r,int dx,int dy){if(topNavigation!=null)topNavigation.setElevation(ui.dp(r.computeVerticalScrollOffset()>16?12:0));}});
        finishFrame(list);return list;
    }
    private void nav(){
        topNavigation=new GlassPanel(this);FrameLayout.LayoutParams navPosition=new FrameLayout.LayoutParams(-2,ui.dp(48),Gravity.TOP|Gravity.CENTER_HORIZONTAL);navPosition.topMargin=ui.dp(18);frame.addView(topNavigation,navPosition);
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=new LinearLayout(this);nav.setPadding(ui.dp(6),ui.dp(4),ui.dp(6),ui.dp(4));nav.setGravity(Gravity.CENTER_VERTICAL);navScroll.addView(nav);topNavigation.addView(navScroll,new FrameLayout.LayoutParams(-2,-1));
        boolean utility=UTILITY.contains(screen);
        for(String target:TABS)nav.addView(ui.navItem(target,!utility&&target.equals(tab),()->showTab(target)),new LinearLayout.LayoutParams(-2,-1));
        View divider=new View(this);divider.setBackgroundColor(0x33ffffff);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(ui.dp(1),ui.dp(22));dl.setMargins(ui.dp(8),0,ui.dp(4),0);dl.gravity=Gravity.CENTER_VERTICAL;nav.addView(divider,dl);
        nav.addView(icon(R.drawable.ic_search,"Search",screen.equals("Search"),()->{saveTabState();history.clear();searchQuery="";showSearch();}));
        nav.addView(icon(R.drawable.ic_bookmark,"Watchlist",screen.equals("Watchlist"),()->{saveTabState();history.clear();showWatchlist();}));
        nav.addView(icon(R.drawable.ic_settings,"Settings",utility&&!screen.equals("Search")&&!screen.equals("Watchlist"),()->{saveTabState();history.clear();showSettings();}));
    }
    private View icon(int res,String label,boolean selected,Runnable click){
        ImageView v=new ImageView(this);v.setImageResource(res);v.setPadding(ui.dp(9),ui.dp(9),ui.dp(9),ui.dp(9));v.setFocusable(true);v.setClickable(true);v.setContentDescription(label);
        v.setOnClickListener(w->click.run());v.setOnFocusChangeListener((w,f)->{FocusMotion.apply(w,f,false);paintIcon(v,selected,f);});paintIcon(v,selected,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40));lp.gravity=Gravity.CENTER_VERTICAL;lp.leftMargin=ui.dp(4);v.setLayoutParams(lp);return v;
    }
    private void paintIcon(ImageView v,boolean selected,boolean focus){v.setBackground(ui.shape(focus||selected?TvUi.WHITE:0x00000000,0,20));v.setColorFilter(focus||selected?TvUi.BG:TvUi.WHITE);}
    private void showTab(String name){saveTabState();history.clear();hideFilters();tab=name;openTab(name,false);}
    private void openTab(String name,boolean restore){
        tab=name;
        switch(name){
            case "All Movies":showAllMovies(restore);break;
            case "HD Movies":showHd(restore);break;
            case "TV Shows":showTvShows(restore);break;
            case "Live TV":showLive(restore);break;
            case "Sports Live":showSports(restore);break;
            default:showHome(restore);
        }
    }
    /** Remembers list scroll, row scroll and grid pages so the tab reopens where the viewer left it. */
    private void saveTabState(){
        if(list==null||!(isTab(screen)||screen.equals("TV Browse")))return;
        FocusAnchor.TabState s=anchor.get(screen);RecyclerView.LayoutManager lm=list.getLayoutManager();
        if(lm!=null)s.list=lm.onSaveInstanceState();
        if(rows!=null)s.rows=rows.scrollStates();
        if(grid!=null&&grid.query!=null&&!grid.pages.isEmpty())gridMemory.put(screen,new GridMemory(grid.query.key(),new TreeMap<>(grid.pages),grid.page,grid.more));
    }
    private void restoreTab(String name,boolean restore){
        FocusAnchor.TabState s=anchor.get(name);
        if(s.list!=null&&list!=null&&list.getLayoutManager()!=null)list.getLayoutManager().onRestoreInstanceState(s.list);
        if(rows!=null){rows.restoreScrollStates(s.rows);rows.focusRow=s.row;rows.focusKey=s.key;rows.forceFocus=restore;}
        if(grid!=null)grid.focusKey=restore?s.key:"";
    }

    // ---- catalog host callbacks ------------------------------------------------------------------
    @Override public void onItemFocused(String rowId,CatalogItem item,int index){
        anchor.focused(screen,rowId,item.key(),index);focusKey=item.key();
        if(screen.equals("Home")){pendingHero=item;main.removeCallbacks(heroUpdate);main.postDelayed(heroUpdate,TvTheme.reduced(this)?450:250);}
        if(grid!=null&&grid.adapter!=null&&index>=grid.adapter.getItemCount()-grid.cols*2)gridMore(grid);
    }
    @Override public void onItemClicked(String rowId,CatalogItem item){openItem(item);}
    @Override public void onItemLongPressed(String rowId,CatalogItem item){
        if(CatalogItem.CHANNEL.equals(item.kind)){boolean added=catalog.toggleFavorite(item.key());Toast.makeText(this,added?"Added to favorites":"Removed from favorites",Toast.LENGTH_SHORT).show();}
        else toggleWatchlist(item.title);
    }
    @Override public int progress(CatalogItem item){
        int pos=prefs.getInt("position."+item.key(),0);if(pos<=0)return 0;
        int duration=prefs.getInt("duration."+item.key(),0);
        return duration>0?Math.max(2,Math.min(100,(int)(pos*100L/duration))):20;
    }
    @Override public boolean favorite(CatalogItem item){return catalog.favorite(item.key());}
    private void onRowsChanged(Set<String> changed){
        if(isDestroyed())return;
        if(rows!=null){
            CatalogViews.RowsAdapter target=rows;int version=generation;
            for(CatalogViews.RowSpec s:target.specs())if(changed.contains(s.id))catalog.row(s.id,ROW_LIMIT,items->{if(rows!=target||generation!=version)return;rowCache.put(s.id,items);List<CatalogItem> shown=transform(s.id,items);target.setRow(s.id,shown);afterRow(s.id,shown);});
            for(String c:changed)if(c.startsWith(CatalogRepository.SYNC_TOKEN))refreshNotes(target,c.substring(CatalogRepository.SYNC_TOKEN.length()));
        }
        if(grid!=null){
            boolean hit=false;for(String c:changed)if(grid.watches.contains(c))hit=true;
            if(hit){if(grid.name.equals("Live TV"))liveLoad(grid);else if(grid.local){grid.pages.clear();grid.page=0;grid.more=true;gridLoad(grid,1);}}
            for(String c:changed)if(c.startsWith(CatalogRepository.SYNC_TOKEN))gridStatus(grid);
        }
        if(screen.equals("Sync"))for(String c:changed)if(c.startsWith(CatalogRepository.SYNC_TOKEN)){updateSyncLines();break;}
    }

    // ---- rows (Home, TV Shows, Sports) --------------------------------------------------------------
    private void loadRows(){
        CatalogViews.RowsAdapter target=rows;if(target==null)return;int version=generation;
        List<String> ids=new ArrayList<>();
        for(CatalogViews.RowSpec s:target.specs()){ids.add(s.id);List<CatalogItem> cached=rowCache.get(s.id);if(cached!=null){List<CatalogItem> shown=transform(s.id,cached);target.setRow(s.id,shown);afterRow(s.id,shown);}}
        catalog.rows(ids,ROW_LIMIT,(id,items)->{if(rows!=target||generation!=version)return;rowCache.put(id,items);List<CatalogItem> shown=transform(id,items);target.setRow(id,shown);afterRow(id,shown);});
        refreshNotes(target,null);
    }
    private void refreshNotes(CatalogViews.RowsAdapter target,String onlySource){
        Map<String,List<String>> bySource=new LinkedHashMap<>();
        for(CatalogViews.RowSpec s:target.specs()){String src=CatalogRepository.sourceFor(s.id);if(src==null||onlySource!=null&&!onlySource.equals(src)&&!onlySource.equals(CatalogFallback.source(s.id)))continue;bySource.computeIfAbsent(src,k->new ArrayList<>()).add(s.id);}
        for(Map.Entry<String,List<String>> e:bySource.entrySet())catalog.freshness(e.getValue().get(0),label->{if(rows!=target)return;for(String id:e.getValue())target.setNote(id,label);});
    }
    private List<CatalogItem> transform(String id,List<CatalogItem> items){
        if(screen.equals("Sports Live")){String sport=uiPrefs().getString("filter.sport","");if(!sport.isEmpty()){List<CatalogItem> out=new ArrayList<>();for(CatalogItem c:items)if(sport.equalsIgnoreCase(c.sport))out.add(c);return out;}}
        return items;
    }
    private void afterRow(String id,List<CatalogItem> items){
        if(screen.equals("Home")&&!heroChosen&&!items.isEmpty()&&!id.equals(CatalogRepository.HOME_CONTINUE)){heroChosen=true;setHero(items.get(0));}
        if(id.equals(CatalogRepository.HOME_LIVE)&&!items.isEmpty())catalog.checkChannels(items.subList(0,Math.min(8,items.size())));
    }
    private CatalogViews.RowsAdapter rowsScreen(String name,boolean cinematic,View header,List<CatalogViews.RowSpec> specs,boolean restore){return rowsScreen(name,cinematic,()->header,specs,restore);}
    /** The header is built after shell() resets per-screen fields such as the hero views. */
    private CatalogViews.RowsAdapter rowsScreen(String name,boolean cinematic,java.util.function.Supplier<View> header,List<CatalogViews.RowSpec> specs,boolean restore){
        RecyclerView rv=shellList(name,cinematic);
        rows=views.new RowsAdapter(header.get(),specs);
        LinearLayoutManager lm=new LinearLayoutManager(this);lm.setInitialPrefetchItemCount(2);rv.setLayoutManager(lm);rv.setAdapter(rows);
        restoreTab(name,restore);loadRows();return rows;
    }
    private void showHome(boolean restore){
        tab="Home";featured=null;featuredItem=null;heroChosen=false;
        boolean tmdb=ApiKeys.metadataReady(this);
        List<CatalogViews.RowSpec> specs=new ArrayList<>();
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_CONTINUE,"Continue Watching",CatalogViews.POSTER,true));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_RECENT,"Recently Added",CatalogViews.POSTER,false));
        if(tmdb)specs.add(new CatalogViews.RowSpec(TmdbCatalogSource.ROW_TRENDING_IN,"Trending in India",CatalogViews.POSTER,false));
        else specs.add(new CatalogViews.RowSpec(WikidataFilmSource.ROW_LATEST,"Latest Indian Releases",CatalogViews.POSTER,false));
        for(String code:new String[]{"te","ta","hi","ml","en","kn"}){
            String row=tmdb?TmdbCatalogSource.latestRow(code):code.equals("en")?null:WikidataFilmSource.row(code);
            if(row!=null)specs.add(new CatalogViews.RowSpec(row,"Latest "+(code.equals("en")?"Hollywood":TmdbCatalogSource.languageName(code))+" Movies",CatalogViews.POSTER,code.equals("kn")||!tmdb));
        }
        specs.add(new CatalogViews.RowSpec(HdCatalogSource.ROW_HD,"Latest HD Movies",CatalogViews.POSTER,true));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_LIVE,"Live TV",CatalogViews.WIDE,false));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_SPORTS,"Sports Live",CatalogViews.WIDE,false));
        specs.add(tmdb?new CatalogViews.RowSpec(TmdbCatalogSource.ROW_RECENT_EPISODES,"Recently Updated TV Episodes",CatalogViews.POSTER,false)
            :new CatalogViews.RowSpec(TvMazeSource.ROW_WORLD,"New TV Episodes",CatalogViews.POSTER,false));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_LIBRARY,"Your library",CatalogViews.POSTER,true));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.HOME_SAMPLES,"Open cinema · free samples",CatalogViews.WIDE,true));
        rowsScreen("Home",true,()->homeHeader(tmdb),specs,restore);
    }
    private View homeHeader(boolean tmdb){
        LinearLayout box=ui.column();
        TextView wordmark=ui.text("S A A N D I O",12,TvUi.MUTED);box.addView(wordmark);ui.gap(box,14);
        LinearLayout hero=ui.column();hero.setGravity(Gravity.CENTER_VERTICAL);heroProvider=ui.text("YOUR CINEMA, CONNECTED",10,TvUi.ACCENT);heroProvider.setLetterSpacing(.2f);hero.addView(heroProvider);ui.gap(hero,10);
        heroTitle=ui.bold("Your next great watch.",compact()?32:38);heroTitle.setMaxLines(compact()?1:2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);hero.addView(heroTitle);ui.gap(hero,10);
        heroMeta=ui.text("Movies  ·  Shows  ·  Live TV  ·  Sports",13,TvUi.WHITE);hero.addView(heroMeta);ui.gap(hero,10);
        heroChips=new LinearLayout(this);heroChips.setGravity(Gravity.CENTER_VERTICAL);hero.addView(heroChips);ui.gap(hero,10);
        heroDescription=ui.text("Loading the latest catalog…",13,TvUi.MUTED);heroDescription.setMaxLines(compact()?1:2);hero.addView(heroDescription,new LinearLayout.LayoutParams(ui.dp(470),-2));ui.gap(hero,16);
        LinearLayout heroActions=new LinearLayout(this);heroActions.setClipChildren(false);
        LinearLayout.LayoutParams actionGap=new LinearLayout.LayoutParams(-2,-2);actionGap.leftMargin=ui.dp(10);
        heroPlay=ui.action("More info",true,()->{if(featuredItem!=null)playItem(featuredItem);});heroActions.addView(heroPlay);
        heroWatch=ui.action("+  Watchlist",false,()->{if(featured!=null)toggleWatchlist(featured);});heroActions.addView(heroWatch,actionGap);
        heroActions.addView(ui.action("More Info",false,()->{if(featuredItem!=null)openItem(featuredItem);}),actionGap);
        hero.addView(heroActions);box.addView(hero,new LinearLayout.LayoutParams(-1,ui.dp(compact()?210:300)));ui.gap(box,10);
        return box;
    }
    private void showTvShows(boolean restore){
        tab="TV Shows";boolean tmdb=ApiKeys.metadataReady(this);
        List<CatalogViews.RowSpec> specs=new ArrayList<>();
        specs.add(new CatalogViews.RowSpec(CatalogRepository.TV_CONTINUE,"Continue Watching",CatalogViews.POSTER,true));
        if(tmdb){
            specs.add(new CatalogViews.RowSpec(TmdbCatalogSource.ROW_RECENT_EPISODES,"Recently aired episodes",CatalogViews.POSTER,false));
            for(String[] l:TmdbCatalogSource.SERIAL_LANGUAGES)specs.add(new CatalogViews.RowSpec(TmdbCatalogSource.serialRow(l[0]),l[1]+" serials",CatalogViews.POSTER,false));
            specs.add(new CatalogViews.RowSpec(TmdbCatalogSource.ROW_TV_NEW,"New Indian series",CatalogViews.POSTER,false));
        }else{
            specs.add(new CatalogViews.RowSpec(TvMazeSource.ROW_INDIA,"Indian TV · latest episodes",CatalogViews.POSTER,false));
            specs.add(new CatalogViews.RowSpec(TvMazeSource.ROW_WORLD,"New episodes worldwide",CatalogViews.POSTER,false));
        }
        LinearLayout header=pageHeader("TV Shows",tmdb
            ?"Telugu, Tamil and Hindi series with their latest aired episode, from TMDB. Episodes play only where an authorized provider streams them; otherwise Saandio shows where to watch."
            :"Series with their latest aired episode, from the TVMaze schedule. Episodes play only where an authorized provider streams them.");
        LinearLayout actions=new LinearLayout(this);actions.setClipChildren(false);
        actions.addView(ui.action("Browse all series",true,()->{saveTabState();history.push(capture());showTvBrowse(false);}));
        header.addView(actions);ui.gap(header,12);
        rowsScreen("TV Shows",false,header,specs,restore);
    }
    private void showSports(boolean restore){
        tab="Sports Live";String sport=uiPrefs().getString("filter.sport","");
        List<CatalogViews.RowSpec> specs=new ArrayList<>();
        specs.add(new CatalogViews.RowSpec(CatalogRepository.SPORTS_LIVE,"Live now (per schedule)",CatalogViews.WIDE,false));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.SPORTS_UPCOMING,"Upcoming",CatalogViews.WIDE,false));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.SPORTS_RESULTS,"Recent results",CatalogViews.WIDE,true));
        specs.add(new CatalogViews.RowSpec(CatalogRepository.SPORTS_OTHER,"Status not confirmed",CatalogViews.WIDE,true));
        LinearLayout header=pageHeader("Sports Live","Match schedules from TheSportsDB. Saandio holds no sports broadcast rights, so matches are not playable here. LIVE appears only when the schedule source reports a match in play.");
        LinearLayout bar=filterBar(header);
        filterButton(bar,"f.sport","Sport · "+(sport.isEmpty()?"All":sport),()->{
            String[] items=new String[SportsSource.SPORTS.length+1];items[0]="All sports";for(int i=0;i<SportsSource.SPORTS.length;i++)items[i+1]=SportsSource.SPORTS[i];
            new AlertDialog.Builder(this).setTitle("Sport").setItems(items,(d,w)->{uiPrefs().edit().putString("filter.sport",w==0?"":items[w]).apply();refocusTag="f.sport";showSports(false);}).setNegativeButton("Cancel",null).show();
        });
        filterButton(bar,"f.refresh","Refresh schedule",()->{catalog.refresh(SportsSource.ID);Toast.makeText(this,"Checking for schedule updates",Toast.LENGTH_SHORT).show();});
        rowsScreen("Sports Live",false,header,specs,restore);
    }

    // ---- grids (All Movies, HD Movies, Live TV, TV browse) ------------------------------------------
    private LinearLayout pageHeader(String title,String subtitle){
        LinearLayout h=ui.column();h.setPadding(0,ui.dp(6),0,ui.dp(10));h.addView(ui.bold(title,30));ui.gap(h,8);
        TextView info=ui.text(subtitle,13,TvUi.MUTED);info.setMaxLines(3);h.addView(info,new LinearLayout.LayoutParams(ui.dp(780),-2));ui.gap(h,14);return h;
    }
    private LinearLayout filterBar(LinearLayout header){
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipChildren(false);
        LinearLayout bar=new LinearLayout(this);bar.setClipChildren(false);bar.setPadding(0,ui.dp(2),0,ui.dp(2));hs.addView(bar);header.addView(hs);ui.gap(header,10);return bar;
    }
    private TextView filterButton(LinearLayout bar,String tag,String label,Runnable click){
        TextView b=ui.action(label,false,click);b.setTag(tag);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=ui.dp(10);bar.addView(b,lp);return b;
    }
    private Grid buildGrid(String name,View header,int style){
        RecyclerView rv=shellList(name,false);
        float density=getResources().getDisplayMetrics().density;int widthDp=(int)(getResources().getDisplayMetrics().widthPixels/density)-100;
        Grid g=new Grid(name,style,Math.max(2,widthDp/((style==CatalogViews.WIDE?224:139)+13)));grid=g;
        g.layout=new GridLayoutManager(this,g.cols);
        CatalogViews.SingleViewAdapter head=new CatalogViews.SingleViewAdapter(header,-1);
        g.adapter=views.new PosterAdapter(name,style,true);
        g.footer=ui.text("",13,TvUi.MUTED);g.footer.setPadding(ui.dp(6),ui.dp(10),0,ui.dp(24));
        ConcatAdapter concat=new ConcatAdapter(new ConcatAdapter.Config.Builder().setStableIdMode(ConcatAdapter.Config.StableIdMode.ISOLATED_STABLE_IDS).build(),head,g.adapter,new CatalogViews.SingleViewAdapter(g.footer,-2));
        g.layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup(){@Override public int getSpanSize(int p){return p==0||p>g.adapter.getItemCount()?g.cols:1;}});
        rv.setLayoutManager(g.layout);rv.setAdapter(concat);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override public void onScrolled(RecyclerView r,int dx,int dy){if(dy>0&&g.layout.findLastVisibleItemPosition()>=g.adapter.getItemCount()-g.cols*2)gridMore(g);}
            @Override public void onScrollStateChanged(RecyclerView r,int state){if(state==RecyclerView.SCROLL_STATE_IDLE&&g.name.equals("Live TV"))checkVisibleChannels(g);}
        });
        return g;
    }
    private void submitGrid(Grid g,List<CatalogItem> items){g.adapter.submitList(items,()->gridFocus(g));}
    private void gridFocus(Grid g){
        if(g.focusKey.isEmpty()||list==null||grid!=g)return;
        int i=g.adapter.indexOf(g.focusKey);if(i<0)return;String key=g.focusKey;g.focusKey="";
        RecyclerView.ViewHolder h=list.findViewHolderForAdapterPosition(i+1);
        if(h!=null){h.itemView.requestFocus();return;}
        list.scrollToPosition(i+1);list.post(()->{if(list==null)return;RecyclerView.ViewHolder again=list.findViewHolderForAdapterPosition(i+1);if(again!=null&&key.equals(again.itemView.getTag()))again.itemView.requestFocus();});
    }
    private boolean restoreGridMemory(Grid g){
        GridMemory m=gridMemory.get(g.name);if(m==null||g.query==null||!m.key.equals(g.query.key()))return false;
        g.pages.putAll(m.pages);g.page=m.page;g.more=m.more;submitGrid(g,flatten(g));g.footer.setText(footerText(g,false,true));return true;
    }
    private List<CatalogItem> flatten(Grid g){List<CatalogItem> all=new ArrayList<>();Set<String> seen=new HashSet<>();for(List<CatalogItem> l:g.pages.values())for(CatalogItem c:l)if(seen.add(c.key()))all.add(c);return all;}
    private String footerText(Grid g,boolean stored,boolean done){
        int n=g.adapter.getItemCount();
        if(n==0)return done?"Nothing matches these filters yet.":"Loading…";
        String base=g.more?(done?"Scroll for more":"Loading more…"):"End of list · "+n+" titles";
        return stored&&done?base+" · showing saved results (offline)":base;
    }
    private void gridLoad(Grid g,int page){
        if(g.loading)return;g.loading=true;int version=generation;g.footer.setText(page==1&&g.adapter.getItemCount()==0?"Loading…":"Loading more…");
        catalog.discover(g.query,page,(p,stored,done)->{
            if(grid!=g||generation!=version)return;
            if(p.page==1&&!stored)for(Iterator<Integer> it=g.pages.keySet().iterator();it.hasNext();)if(it.next()>1&&g.local)it.remove();
            g.pages.put(p.page,p.items);if(p.page>g.page)g.page=p.page;g.more=p.hasMore();if(done)g.loading=false;
            List<CatalogItem> all=flatten(g);g.adapter.submitList(all,()->{gridFocus(g);g.footer.setText(footerText(g,stored,done));});
        },err->{if(grid!=g)return;g.loading=false;g.footer.setText(err);});
    }
    private void gridMore(Grid g){if(g.query!=null&&g.more&&!g.loading&&g.page>0)gridLoad(g,g.page+1);}
    private void gridStatus(Grid g){
        if(g.status==null)return;String row=g.name.equals("Live TV")?LiveTvSource.ROW:g.name.equals("HD Movies")?HdCatalogSource.ROW_HD:g.query!=null&&!g.query.movie()?TmdbCatalogSource.ROW_TV_NEW:CatalogRepository.HOME_RECENT;
        catalog.freshness(row,label->{if(grid==g&&g.status!=null)g.status.setText(label);});
    }
    private void reopenGrid(String name,String tag){anchor.get(name).list=null;gridMemory.remove(name);refocusTag=tag;switch(name){case "HD Movies":showHd(false);break;case "TV Browse":showTvBrowse(false);break;case "Live TV":showLive(false);break;default:showAllMovies(false);}}
    private void showAllMovies(boolean restore){showDiscover("All Movies","filter.all_movies","movie",restore);}
    private void showTvBrowse(boolean restore){showDiscover("TV Browse","filter.tv_browse","tv",restore);}
    private void showDiscover(String name,String prefKey,String kind,boolean restore){
        if(isTab(name))tab=name;
        DiscoverQuery q=DiscoverQuery.from(uiPrefs().getString(prefKey,""),kind);boolean movie=q.movie(),tmdb=ApiKeys.metadataReady(this);
        LinearLayout header=pageHeader(movie?"All Movies":"All TV Series",!tmdb
            ?(movie?"Recent Indian-language films from Wikidata (CC0), newest release first. A Play button appears only when an authorized provider streams the title."
                :"Series with recently aired episodes from TVMaze (CC BY-SA). A Play button appears only when an authorized provider streams the title.")
            :movie?"Movies released in India and in Indian languages, newest release first, from TMDB. Ratings are TMDB community scores. A Play button appears only when an authorized provider streams the title."
            :"Indian and Indian-language series from TMDB, newest first. Episode lists include season, episode and air date.");
        if(!movie){header.addView(ui.action("‹ Back",false,this::back),new LinearLayout.LayoutParams(-2,-2));ui.gap(header,12);}
        LinearLayout bar=filterBar(header);
        filterButton(bar,"f.lang","Language · "+(q.languageCode.isEmpty()?"All":TmdbCatalogSource.languageName(q.languageCode)),()->catalog.languages(movie?CatalogItem.MOVIE:CatalogItem.SERIES,langs->{
            String[] labels=new String[langs.size()+1];labels[0]="All languages";for(int i=0;i<langs.size();i++)labels[i+1]=langs.get(i)[1];
            pick("Language",labels,w->{q.languageCode=w==0?"":langs.get(w-1)[0];saveQuery(prefKey,q);reopenGrid(name,"f.lang");});
        }));
        filterButton(bar,"f.year","Year · "+(q.year>0?String.valueOf(q.year):"Any"),()->catalog.years(movie?CatalogItem.MOVIE:CatalogItem.SERIES,years->{
            String[] labels=new String[years.size()+1];labels[0]="Any year";for(int i=0;i<years.size();i++)labels[i+1]=String.valueOf(years.get(i));
            pick("Release year",labels,w->{q.year=w==0?0:years.get(w-1);saveQuery(prefKey,q);reopenGrid(name,"f.year");});
        }));
        filterButton(bar,"f.genre","Genre · "+(q.genreName.isEmpty()?"All":q.genreName),()->catalog.genres(movie,genres->{
            if(genres.isEmpty()){notice("Genres unavailable","Genre names appear once the catalog has loaded. Check the connection and try again shortly.");return;}
            List<Map.Entry<Integer,String>> entries=new ArrayList<>(genres.entrySet());String[] labels=new String[entries.size()+1];labels[0]="All genres";for(int i=0;i<entries.size();i++)labels[i+1]=entries.get(i).getValue();
            pick("Genre",labels,w->{q.genreId=w==0?0:entries.get(w-1).getKey();q.genreName=w==0?"":entries.get(w-1).getValue();saveQuery(prefKey,q);reopenGrid(name,"f.genre");});
        }));
        filterButton(bar,"f.sort","Sort · "+DiscoverQuery.sortLabel(q.sort),()->{
            String[] labels=new String[DiscoverQuery.SORTS.size()];for(int i=0;i<labels.length;i++)labels[i]=DiscoverQuery.sortLabel(DiscoverQuery.SORTS.get(i));
            pick("Sort",labels,w->{q.sort=DiscoverQuery.SORTS.get(w);saveQuery(prefKey,q);reopenGrid(name,"f.sort");});
        });
        filterButton(bar,"f.rating",(tmdb?"TMDB rating · ":"Rating · ")+(q.minRating>0?trim(q.minRating)+"+":"Any"),()->{
            float[] values={0,5,6,7,8};String[] labels={"Any rating","5+","6+","7+","8+"};
            pick(tmdb?"Minimum TMDB rating":"Minimum rating",labels,w->{q.minRating=values[w];saveQuery(prefKey,q);reopenGrid(name,"f.rating");});
        });
        if(movie)filterButton(bar,"f.quality","Quality · "+(q.minHeight>0?PlaybackPolicy.heightLabel(q.minHeight)+"+ verified":"Any"),()->{
            int[] values={0,720,1080,2160};String[] labels={"Any (metadata catalog)","720p+ verified","1080p+ verified","4K verified"};
            pick("Verified quality",labels,w->{q.minHeight=values[w];saveQuery(prefKey,q);reopenGrid(name,"f.quality");});
        });
        DiscoverQuery defaults=new DiscoverQuery();defaults.kind=q.kind;
        if(!q.key().equals(defaults.key()))filterButton(bar,"f.reset","Reset filters",()->{uiPrefs().edit().remove(prefKey).apply();reopenGrid(name,"f.sort");});
        TextView status=ui.text("",12,TvUi.MUTED);header.addView(status);ui.gap(header,8);
        if(q.minHeight>0){header.addView(ui.text("Verified quality comes only from authorized playback partners, so this view lists partner titles rather than the full TMDB catalog.",12,TvUi.ACCENT));ui.gap(header,8);}
        Grid g=buildGrid(name,header,CatalogViews.POSTER);g.query=q;g.status=status;g.local=!q.remote()||!tmdb;
        if(g.local)g.watches.addAll(Arrays.asList(HdCatalogSource.ROW_HD,HdCatalogSource.ROW_PLAYABLE));
        if(!tmdb)g.watches.addAll(movie?Collections.singletonList(WikidataFilmSource.ROW_LATEST):Arrays.asList(TvMazeSource.ROW_INDIA,TvMazeSource.ROW_WORLD));
        restoreTab(name,restore);
        if(!restoreGridMemory(g))gridLoad(g,1);
        gridStatus(g);
    }
    private void showHd(boolean restore){
        tab="HD Movies";
        DiscoverQuery q=DiscoverQuery.from(uiPrefs().getString("filter.hd",""),"movie");if(q.minHeight<720)q.minHeight=720;if(!q.sort.equals("added"))q.sort="newest";
        LinearLayout header=pageHeader("HD Movies","Only titles an authorized playback partner streams at 720p or better. Quality is read from the partner's stream renditions, never from words like \"HDRip\" in a title or web page.");
        LinearLayout bar=filterBar(header);
        filterButton(bar,"f.quality","Quality · "+PlaybackPolicy.heightLabel(q.minHeight)+"+",()->{
            int[] values={720,1080,2160};String[] labels={"720p and above","1080p and above","4K"};
            pick("Verified quality",labels,w->{q.minHeight=values[w];saveQuery("filter.hd",q);reopenGrid("HD Movies","f.quality");});
        });
        filterButton(bar,"f.lang","Language · "+(q.languageCode.isEmpty()?"All":LanguageTaxonomy.canonical(q.languageCode)),()->{
            String[] labels=new String[LanguageTaxonomy.INDIAN.size()+2];labels[0]="All languages";labels[1]="English";for(int i=0;i<LanguageTaxonomy.INDIAN.size();i++)labels[i+2]=LanguageTaxonomy.INDIAN.get(i);
            pick("Language",labels,w->{q.languageCode=w==0?"":labels[w];saveQuery("filter.hd",q);reopenGrid("HD Movies","f.lang");});
        });
        filterButton(bar,"f.sort","Sort · "+DiscoverQuery.sortLabel(q.sort),()->pick("Sort",new String[]{"Newest first","Recently added"},w->{q.sort=w==0?"newest":"added";saveQuery("filter.hd",q);reopenGrid("HD Movies","f.sort");}));
        filterButton(bar,"f.addons","Partners & add-ons",()->{saveTabState();history.push(capture());showAddons();});
        TextView status=ui.text("",12,TvUi.MUTED);header.addView(status);ui.gap(header,8);
        Grid g=buildGrid("HD Movies",header,CatalogViews.POSTER);g.query=q;g.status=status;g.local=true;g.watches.addAll(Arrays.asList(HdCatalogSource.ROW_HD,HdCatalogSource.ROW_PLAYABLE));
        restoreTab("HD Movies",restore);gridLoad(g,1);gridStatus(g);
    }
    private void showLive(boolean restore){
        tab="Live TV";android.content.SharedPreferences u=uiPrefs();
        LinearLayout header=pageHeader("Live TV",LiveTvSource.ATTRIBUTION+". Online, Offline and Unchecked show the result of Saandio's last playback check. Long-press a channel to add it to favorites. No programme guide (EPG) source is configured.");
        if(!prefs.getBoolean("addon.news",true)){header.addView(ui.text("The TV channels add-on is turned off.",14,TvUi.ACCENT));ui.gap(header,8);header.addView(ui.action("Turn on TV channels",true,()->{prefs.edit().putBoolean("addon.news",true).apply();catalog.refresh(LiveTvSource.ID);showLive(false);}));ui.gap(header,12);}
        LinearLayout bar=filterBar(header);
        String lang=u.getString("live.language",""),genre=u.getString("live.genre","");boolean favOnly=u.getBoolean("live.favonly",false);
        filterButton(bar,"f.lang","Language · "+(lang.isEmpty()?"All":lang),()->channelFacet(true));
        filterButton(bar,"f.genre","Genre · "+(genre.isEmpty()?"All":genre),()->channelFacet(false));
        filterButton(bar,"f.fav","Favorites only · "+(favOnly?"On":"Off"),()->{u.edit().putBoolean("live.favonly",!favOnly).apply();reopenGrid("Live TV","f.fav");});
        filterButton(bar,"f.check","Check channels now",()->{if(grid!=null)checkVisibleChannels(grid);Toast.makeText(this,"Checking visible channels",Toast.LENGTH_SHORT).show();});
        TextView status=ui.text("",12,TvUi.MUTED);header.addView(status);ui.gap(header,8);
        Grid g=buildGrid("Live TV",header,CatalogViews.WIDE);g.status=status;g.local=true;g.more=false;
        g.watches.addAll(Arrays.asList(LiveTvSource.ROW,CatalogRepository.ROW_FAVORITES,CatalogRepository.HOME_LIVE));
        restoreTab("Live TV",restore);liveLoad(g);gridStatus(g);
    }
    private void liveLoad(Grid g){
        int version=generation;android.content.SharedPreferences u=uiPrefs();String lang=u.getString("live.language",""),genre=u.getString("live.genre","");boolean favOnly=u.getBoolean("live.favonly",false);
        catalog.row(LiveTvSource.ROW,3000,items->{
            if(grid!=g||generation!=version)return;
            Set<String> fav=catalog.favorites();List<CatalogItem> shown=new ArrayList<>();
            for(CatalogItem c:items){if(!lang.isEmpty()&&!lang.equals(c.title.language))continue;if(!genre.isEmpty()&&!genre.equalsIgnoreCase(c.title.group))continue;if(favOnly&&!fav.contains(c.key()))continue;shown.add(c);}
            g.adapter.submitList(shown,()->{gridFocus(g);g.footer.setText(shown.isEmpty()?(items.isEmpty()?"Channel directory not loaded yet.":"No channels match these filters."):shown.size()+" channels");if(g.adapter.getItemCount()>0&&list!=null)list.post(()->checkVisibleChannels(g));});
        });
    }
    private void channelFacet(boolean language){
        catalog.row(LiveTvSource.ROW,3000,items->{
            TreeMap<String,Integer> counts=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for(CatalogItem c:items){String v=language?c.title.language:c.title.group;if(v!=null&&!v.isEmpty())counts.merge(v,1,Integer::sum);}
            List<String> values=new ArrayList<>(counts.keySet());String[] labels=new String[values.size()+1];labels[0]=language?"All languages":"All genres";for(int i=0;i<values.size();i++)labels[i+1]=values.get(i)+" · "+counts.get(values.get(i));
            pick(language?"Language":"Genre",labels,w->{uiPrefs().edit().putString(language?"live.language":"live.genre",w==0?"":values.get(w-1)).apply();reopenGrid("Live TV",language?"f.lang":"f.genre");});
        });
    }
    private void checkVisibleChannels(Grid g){
        if(grid!=g||g.layout==null)return;int first=Math.max(1,g.layout.findFirstVisibleItemPosition()),last=g.layout.findLastVisibleItemPosition();
        List<CatalogItem> current=g.adapter.getCurrentList();List<CatalogItem> visible=new ArrayList<>();
        for(int p=first;p<=last;p++){int i=p-1;if(i>=0&&i<current.size())visible.add(current.get(i));}
        if(!visible.isEmpty())catalog.checkChannels(visible);
    }
    private void pick(String title,String[] labels,java.util.function.IntConsumer chosen){new AlertDialog.Builder(this).setTitle(title).setItems(labels,(d,w)->chosen.accept(w)).setNegativeButton("Cancel",null).show();}
    private void saveQuery(String prefKey,DiscoverQuery q){uiPrefs().edit().putString(prefKey,q.json().toString()).apply();}
    private static String trim(float f){return f==(int)f?String.valueOf((int)f):String.valueOf(f);}

    // ---- hero ----------------------------------------------------------------------------------------
    private void setHero(CatalogItem c){
        if(heroTitle==null)return;featuredItem=c;featured=c.title;Library.Title title=c.title;
        heroTitle.setText(title.name);heroProvider.setText(kindLabel(c));
        heroMeta.setText(join(c.year()>0?String.valueOf(c.year()):"",title.language,CatalogItem.CHANNEL.equals(c.kind)?"Live channel":CatalogItem.EVENT.equals(c.kind)?CatalogViews.time(c.startsAt):c.episodeLabel,c.rating>0&&!c.ratingSource.isEmpty()?String.format(Locale.US,"★ %.1f %s",c.rating,c.ratingSource):""));
        if(heroChips!=null){heroChips.removeAllViews();
            if(c.verifiedHeight>0)heroChips.addView(ui.badge(PlaybackPolicy.heightLabel(c.verifiedHeight)+" verified",0xcc1f4f8a));
            for(String g:GenreTaxonomy.of(title)){
                if(g.equals(GenreTaxonomy.OTHER))continue;
                LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=ui.dp(8);
                heroChips.addView(ui.chip(g),heroChips.getChildCount()==0?new LinearLayout.LayoutParams(-2,-2):gap);
                if(heroChips.getChildCount()>=4)break;
            }
        }
        if(heroWatch!=null)heroWatch.setText(prefs.getBoolean("saved."+title.key(),false)?"✓  In watchlist":"+  Watchlist");
        heroDescription.setText(title.description.isEmpty()?"Open for details, availability and where to watch.":title.description);
        if(heroPlay!=null){
            heroPlay.setText(CatalogItem.EVENT.equals(c.kind)?"Match details":"…");
            if(!CatalogItem.EVENT.equals(c.kind))catalog.resolve(c,r->{if(featuredItem!=c||heroPlay==null)return;heroPlay.setText(r.playable()?(CatalogItem.CHANNEL.equals(c.kind)?"▶  Watch live":"▶  Play"):"tmdb".equals(c.title.provider)?"Where to watch":"Details");});
        }
        String art=title.background.isEmpty()?title.poster:title.background;
        int known=ColorExtractor.cached(art);if(known!=0)tintHero(known);
        if(backdrop!=null)Artwork.load(backdrop,art,bitmap->{if(featured!=title||isDestroyed())return;tintHero(ColorExtractor.extract(art,bitmap));if(topNavigation!=null&&backdrop!=null)topNavigation.artwork(backdrop.getDrawable());});
        if(trailers!=null&&!CatalogItem.CHANNEL.equals(c.kind)&&!CatalogItem.EVENT.equals(c.kind))trailers.consider(title);
    }
    private static String kindLabel(CatalogItem c){
        if(CatalogItem.CHANNEL.equals(c.kind))return "LIVE TV";
        if(CatalogItem.EVENT.equals(c.kind))return (c.sport.isEmpty()?"SPORTS":c.sport.toUpperCase(Locale.ROOT))+(c.live()?" · LIVE":"");
        String lang=c.title.language.toUpperCase(Locale.ROOT);
        return CatalogItem.SERIES.equals(c.kind)?(lang.isEmpty()?"SERIES":lang+" · SERIES"):lang.isEmpty()?"MOVIE":lang;
    }
    /** Plays only what the resolver confirms; everything else opens details with where-to-watch. */
    private void playItem(CatalogItem c){
        if(CatalogItem.EVENT.equals(c.kind)){openItem(c);return;}
        catalog.resolve(c,r->{if(isDestroyed())return;if(r.playable()&&r.title!=null&&!"series".equals(r.title.type))findStreams(r.title);else openItem(c);});
    }
    private void toggleWatchlist(Library.Title title){
        boolean value=!prefs.getBoolean("saved."+title.key(),false);prefs.edit().putBoolean("saved."+title.key(),value).apply();persist(title);
        if(heroWatch!=null&&featured!=null&&featured.key().equals(title.key()))heroWatch.setText(value?"✓  In watchlist":"+  Watchlist");
        Toast.makeText(this,value?"Saved to watchlist":"Removed from watchlist",Toast.LENGTH_SHORT).show();
    }
    private void tintHero(int color){
        int ink=TvTheme.darken(color==0?TvUi.BG:color,.62f);
        if(heroShade!=null)heroShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{TvUi.BG,TvTheme.blend(ink,TvUi.BG,.25f),0x2208090c}));
        if(heroFade!=null)heroFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x0008090c,TvTheme.blend(ink,TvUi.BG,.45f),TvUi.BG}));
        if(frame!=null)frame.setBackgroundColor(TvTheme.darken(ink,.35f));
    }

    // ---- legacy shelves (search results, provider pages, episodes, watchlist) ------------------------
    private void shelf(LinearLayout parent,String name,List<Library.Title> titles,boolean wide){
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(ui.bold(name,20),new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(heading);ui.gap(parent,8);
        boolean landscape=wide||compact();RecyclerView row=new RecyclerView(this);row.setHorizontalScrollBarEnabled(false);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(ui.dp(4),ui.dp(6),ui.dp(8),ui.dp(8));
        LinearLayoutManager layout=new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false);row.setLayoutManager(layout);row.setItemAnimator(null);row.setNestedScrollingEnabled(false);
        row.setAdapter(new ShelfAdapter(titles,landscape));parent.addView(row,new LinearLayout.LayoutParams(-1,ui.dp(landscape?184:244)));ui.gap(parent,20);
    }
    private final class ShelfAdapter extends RecyclerView.Adapter<ShelfHolder>{
        final List<Library.Title> titles;final boolean wide;
        ShelfAdapter(List<Library.Title> titles,boolean wide){this.titles=titles;this.wide=wide;setHasStableIds(true);}
        @Override public long getItemId(int index){return CatalogItem.stableId(titles.get(index).key());}
        @Override public ShelfHolder onCreateViewHolder(android.view.ViewGroup parent,int type){FrameLayout slot=new FrameLayout(MainActivity.this);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(ui.dp(wide?224:139),-1);p.rightMargin=ui.dp(13);slot.setLayoutParams(p);slot.setClipChildren(false);return new ShelfHolder(slot);}
        @Override public void onBindViewHolder(ShelfHolder holder,int index){
            FrameLayout slot=(FrameLayout)holder.itemView;slot.removeAllViews();Library.Title t=titles.get(index);slot.addView(card(t,wide),new FrameLayout.LayoutParams(-1,-2));
            if(index+1<titles.size())Artwork.prefetch(slot.getContext(),titles.get(index+1).poster);
        }
        @Override public int getItemCount(){return titles.size();}
    }
    private static final class ShelfHolder extends RecyclerView.ViewHolder{ShelfHolder(View view){super(view);}}
    private LinearLayout.LayoutParams cardParams(boolean wide){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(wide?224:139),-2);p.setMargins(0,0,ui.dp(13),0);return p;}
    private View card(Library.Title t,boolean wide){
        LinearLayout card=ui.column();card.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(6));card.setTag(t.key());card.setContentDescription(t.name+", "+t.providerName);
        FrameLayout artwork=new FrameLayout(this);int tint=0xff1b2638|(t.name.hashCode()&0x00181818);artwork.setBackground(ui.shape(tint,0,18));artwork.setClipToOutline(true);
        TextView fallback=ui.bold(t.name.substring(0,Math.min(2,t.name.length())).toUpperCase(Locale.ROOT),30);fallback.setGravity(Gravity.CENTER);artwork.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));Artwork.load(image,t.poster);
        if(position(t)>0){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgress(20);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,ui.dp(3),Gravity.BOTTOM);artwork.addView(progress,pp);}
        card.addView(artwork,new LinearLayout.LayoutParams(-1,ui.dp(wide?124:184)));ui.gap(card,8);TextView title=ui.bold(t.name,13);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);ui.gap(card,4);TextView subtitle=ui.text(join(t.year,t.language,t.type.equals("tv")?"LIVE":t.providerName),10,TvUi.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);card.addView(subtitle);
        ui.focusCard(card,()->{focusKey=t.key();
            if(Library.netmirror(t.provider)&&t.name.startsWith("NetMirror · ")&&enriching.add(t.key()))repository.io.execute(()->{try{Library.Title full=repository.details(t);runOnUiThread(()->{enriching.remove(t.key());if(isDestroyed())return;title.setText(full.name);persist(full);});}catch(Exception ignored){runOnUiThread(()->enriching.remove(t.key()));}});
        });card.setOnClickListener(v->openDetails(t));
        card.setOnLongClickListener(v->{toggleWatchlist(t);return true;});return card;
    }
    private void grid(LinearLayout parent,List<Library.Title> titles){grid(parent,titles,false);}
    private void grid(LinearLayout parent,List<Library.Title> titles,boolean wide){
        LinearLayout row=null;int width=getResources().getDisplayMetrics().widthPixels;int cols=Math.max(2,(int)(width/getResources().getDisplayMetrics().density-160)/(wide?237:153));
        for(int i=0;i<titles.size();i++){if(i%cols==0){row=new LinearLayout(this);row.setClipChildren(false);parent.addView(row);ui.gap(parent,14);}row.addView(card(titles.get(i),wide),cardParams(wide));}
    }

    // ---- legacy add-ons ------------------------------------------------------------------------------
    private void refreshAddons(){List<Library.Addon> ordered=new ArrayList<>(Library.addons());ordered.sort(Comparator.comparing(a->Library.netmirror(a.id)?1:0));for(Library.Addon addon:ordered)if(repository.enabled(addon.id))load(addon,1);}
    private void load(Library.Addon addon,int page){
        if(!loading.add(addon.id))return;states.put(addon.id,"Loading catalog…");
        String signature=configSignature(addon.id);
        repository.io.execute(()->{
            ProviderRepository.Catalog result=null;String failure=null;try{result=repository.load(addon,"",page);}catch(Exception e){failure=ProviderRepository.message(e);}
            ProviderRepository.Catalog value=result;String error=failure;
            runOnUiThread(()->{if(isDestroyed())return;loading.remove(addon.id);
                if(!signature.equals(configSignature(addon.id))){load(addon,1);return;}
                research.record(addon.id,"catalog",value!=null&&!value.stale);
                if(value!=null){if(page==1)catalogs.put(addon.id,value);browsePages.put(addon.id,value);states.put(addon.id,value.status);}else states.put(addon.id,error);
                if(screen.equals("Provider:"+addon.id))showProvider(addon,value==null?Math.max(1,page-1):page);else if(screen.equals("Add-ons")){TextView status=addonStatus.get(addon.id),count=addonCount.get(addon.id);if(status!=null)status.setText(states.get(addon.id));if(count!=null)count.setText(value==null?"Needs attention":value.total+" listed items");}
            });
        });
    }
    private String configSignature(String id){return repository.language(id)+"|"+repository.genre(id)+"|"+repository.endpoint(id)+(Library.netmirror(id)?"|"+prefs.getString("netmirror.api","")+"|"+prefs.getInt("netmirror.session.revision",0):"");}
    private void showProvider(Library.Addon addon,int page){
        sourcePage=page;shell("Provider:"+addon.id,true);heading(addon.name,states.getOrDefault(addon.id,"Catalog"));
        LinearLayout controls=new LinearLayout(this);controls.addView(ui.action("‹ Back",false,this::back));
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.leftMargin=ui.dp(10);
        controls.addView(ui.action("Filters",false,this::showFilters),margin);
        if(addon.id.equals("movierulz")||addon.id.equals("news")){controls.addView(ui.action("Language · "+repository.language(addon.id),false,()->chooseFacet(addon,true)),margin);if(addon.id.equals("news"))controls.addView(ui.action("Genre · "+repository.genre(addon.id),false,()->chooseFacet(addon,false)),margin);}
        body.addView(controls);ui.gap(body,20);
        ProviderRepository.Catalog cat=browsePages.get(addon.id);if(cat==null||cat.page!=page)cat=catalogs.get(addon.id);
        if(cat!=null){BrowseFilter f=BrowseFilter.load(uiPrefs());List<Library.Title> shown=new ArrayList<>();for(Library.Title t:cat.titles)if(f.matches(t,position(t)))shown.add(t);shown=CatalogSort.by(shown,f.sort);
            if(!shown.isEmpty())Artwork.load(backdrop,shown.get(0).background);if(addon.id.equals("news")){Map<String,List<Library.Title>> sections=new LinkedHashMap<>();for(Library.Title t:shown)sections.computeIfAbsent(join(t.language,t.group),k->new ArrayList<>()).add(t);for(Map.Entry<String,List<Library.Title>> section:sections.entrySet())shelf(body,section.getKey(),section.getValue(),true);}else grid(body,shown);
            ui.gap(body,16);LinearLayout paging=new LinearLayout(this);if(page>1)paging.addView(ui.action("Previous",false,()->load(addon,page-1)));if(cat.hasMore)paging.addView(ui.action(loading.contains(addon.id)?"Loading…":"Next page",true,()->load(addon,page+1)));body.addView(paging);ui.gap(body,10);body.addView(ui.text("Page "+page+" · "+cat.total+" listed items",12,TvUi.MUTED));}
        else{body.addView(ui.action("Load catalog",true,()->load(addon,1)));ui.gap(body,10);body.addView(ui.action("Provider settings",false,()->configure(addon)));}
    }
    private void chooseFacet(Library.Addon addon,boolean language){String[] choices=language?addon.id.equals("movierulz")?new String[]{"Telugu + Hindi","Telugu","Hindi","All","Tamil","Kannada","Malayalam"}:new String[]{"Telugu","Hindi","All","Tamil","Kannada","Malayalam","Bengali","Marathi","Punjabi","Gujarati","Urdu","Odia","Assamese","English","Bhojpuri","Sanskrit","Other Indian languages"}:new String[]{"All","News","General","Entertainment","Movies","Music","Religious","Kids","Sports","Education","Culture","Documentary","Business"};new AlertDialog.Builder(this).setTitle(language?"Language":"Genre").setItems(choices,(d,w)->{prefs.edit().putString((language?"browse.language.":"browse.genre.")+addon.id,choices[w]).apply();browsePages.remove(addon.id);catalogs.remove(addon.id);load(addon,1);showProvider(addon,1);}).setNegativeButton("Cancel",null).show();}

    // ---- details ---------------------------------------------------------------------------------------
    private CatalogItem itemFor(Library.Title t){
        if(detailItem!=null&&detailItem.key().equals(t.key()))return detailItem;
        CatalogItem c=CatalogItem.of(t);int[] ids=TmdbCatalogSource.ids(t);
        if(ids!=null){c.tmdbId=ids[1];c.kind=ids[0]==1?CatalogItem.SERIES:CatalogItem.MOVIE;}
        return c;
    }
    private void showDetails(Library.Title title){
        detailTitle=title;CatalogItem item=itemFor(title);detailItem=item;boolean tmdbTitle="tmdb".equals(title.provider);boolean channel=CatalogItem.CHANNEL.equals(item.kind);
        boolean metaTitle=PlaybackSourceResolver.metadataOnly(title)&&!channel,openTitle=metaTitle&&!tmdbTitle;
        shell("Details",true);Artwork.load(backdrop,title.background.isEmpty()?title.poster:title.background);
        TextView back=ui.action("‹  Back",false,this::back);body.addView(back,new LinearLayout.LayoutParams(-2,ui.dp(38)));ui.gap(body,26);
        String kicker=join(LanguageTaxonomy.of(title),title.type.equals("series")?"Series":title.type.equals("episode")?"Episode":channel?"Live":"Movie").toUpperCase(Locale.ROOT);
        TextView provider=ui.text(kicker,11,TvUi.ACCENT);provider.setLetterSpacing(.18f);body.addView(provider);ui.gap(body,12);TextView name=ui.bold(title.name,34);body.addView(name);ui.gap(body,12);
        TextView metaLine=ui.text(join(title.released.length()==10?title.released:title.year,title.language,title.genre,item.rating>0&&!item.ratingSource.isEmpty()?String.format(Locale.US,"★ %.1f %s",item.rating,item.ratingSource):title.rating.isEmpty()?"":"★ "+title.rating),14,TvUi.WHITE);body.addView(metaLine);ui.gap(body,16);
        TextView description=ui.text(title.description.isEmpty()?"Loading title details…":title.description,15,TvUi.MUTED);description.setMaxWidth(ui.dp(650));body.addView(description);ui.gap(body,22);
        LinearLayout actions=new LinearLayout(this);actions.setClipChildren(false);
        final Library.Title[] target={metaTitle?null:title};final String[] unavailable={""};
        TextView where=ui.bold("Where to watch",19);
        String initial=metaTitle?"Checking availability…":title.type.equals("series")?"Browse episodes":channel?"▶  Watch live":position(title)>0?"▶  Resume":"▶  Play";
        TextView play=ui.action(initial,true,()->{
            if(!metaTitle){findStreams(detailTitle);return;}
            if(target[0]!=null)findStreams(target[0]);
            else if(openTitle)notice("Not streaming in Saandio",unavailable[0].isEmpty()?"No authorized provider lists this title.":unavailable[0]);
            else if("series".equals(title.type))showTmdbEpisodes(item,-1,true);
            else{where.setFocusable(true);where.requestFocus();}
        });actions.addView(play);LinearLayout.LayoutParams next=new LinearLayout.LayoutParams(-2,-2);next.setMargins(ui.dp(12),0,0,0);
        TextView saved=ui.action(prefs.getBoolean("saved."+title.key(),false)?"✓  In watchlist":"+  Watchlist",false,()->{boolean value=!prefs.getBoolean("saved."+title.key(),false);prefs.edit().putBoolean("saved."+title.key(),value).apply();persist(title);showDetails(detailTitle);});actions.addView(saved,next);
        TextView episodes=ui.action("Episodes",false,()->{if(tmdbTitle)showTmdbEpisodes(item,-1,true);else showEpisodes(detailTitle,-1);});if(title.type.equals("series")&&!openTitle)actions.addView(episodes,next);
        if(channel)actions.addView(ui.action(catalog.favorite(title.key())?"★  Favorite":"☆  Add to favorites",false,()->{catalog.toggleFavorite(title.key());showDetails(title);}),next);
        body.addView(actions);ui.gap(body,12);
        TextView availability=ui.text(tmdbTitle?"Metadata from TMDB.":"Source: "+title.providerName+(channel?" · "+LiveTvSource.ATTRIBUTION:""),12,TvUi.MUTED);body.addView(availability);ui.gap(body,22);
        int version=generation;
        if(metaTitle||channel)catalog.resolve(item,r->{
            if(isDestroyed()||generation!=version)return;
            if(metaTitle){if(r.playable()&&r.title!=null){target[0]=r.title;play.setText("▶  Play");}else{unavailable[0]=r.reason;play.setText(openTitle?"Not streaming here":"series".equals(title.type)?"Episodes":"Where to watch");}}
            availability.setText((tmdbTitle?"Metadata from TMDB. ":"Source: "+title.providerName+". ")+r.reason);
        });
        LinearLayout whereBox=ui.column();
        if(tmdbTitle){
            body.addView(where);ui.gap(body,8);body.addView(whereBox);ui.gap(body,18);
            TextView wait=ui.text("Loading streaming services…",14,TvUi.MUTED);whereBox.addView(wait);
            catalog.watchProviders(title,lines->{if(isDestroyed()||generation!=version)return;whereBox.removeAllViews();
                if(lines.isEmpty())whereBox.addView(ui.text(ApiKeys.metadataReady(this)?"No streaming service in India is listed for this title yet.":"Streaming services are unavailable right now.",14,TvUi.MUTED));
                else for(String line:lines){whereBox.addView(ui.text(line,15,TvUi.WHITE));ui.gap(whereBox,6);}
                ui.gap(whereBox,4);whereBox.addView(ui.text("Streaming availability data: JustWatch, via TMDB.",11,TvUi.MUTED));
            });
        }
        LinearLayout ratings=new LinearLayout(this);body.addView(ratings);ui.gap(body,16);
        TextView tagline=ui.text("",15,TvUi.ACCENT);tagline.setVisibility(View.GONE);body.addView(tagline);ui.gap(body,10);
        body.addView(ui.bold("Cast & creators",19));ui.gap(body,8);TextView cast=ui.text(title.cast.isEmpty()?"Cast information has not been supplied by this source.":title.cast,14,TvUi.MUTED);body.addView(cast);ui.gap(body,8);
        LinearLayout people=new LinearLayout(this);people.setVisibility(View.GONE);body.addView(people);ui.gap(body,18);
        LinearLayout extra=ui.column();body.addView(extra);
        body.addView(ui.bold("Your review",19));ui.gap(body,8);TextView note=ui.text(prefs.getString("review."+title.key(),"You haven't reviewed this title yet."),14,TvUi.MUTED);body.addView(note);ui.gap(body,12);body.addView(ui.action("Write a review",false,()->review(title)));
        if(!channel){ui.gap(body,16);body.addView(ui.action("Correct metadata match",false,()->correctMatch(title)));}
        play.requestFocus();
        if(!metaTitle)repository.io.execute(()->{try{Library.Title full=repository.details(title);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;detailTitle=full;name.setText(full.name);metaLine.setText(join(full.year,full.language,full.genre,full.rating.isEmpty()?"":"★ "+full.rating));description.setText(full.description.isEmpty()?"No synopsis has been supplied by this source.":full.description);cast.setText(full.cast.isEmpty()?"Cast information has not been supplied by this source.":full.cast);persist(full);if(full.type.equals("series")&&episodes.getParent()==null)actions.addView(episodes,next);});}catch(Exception e){runOnUiThread(()->{if(generation==version)description.setText(title.description.isEmpty()?"Details unavailable. "+ProviderRepository.message(e):title.description);});}});
        if(channel)return;
        repository.io.execute(()->{try{org.json.JSONObject rec=ApiKeys.metadataReady(this)?metadata.enrich(title):null;runOnUiThread(()->{if(isDestroyed()||generation!=version||detailTitle==null||!detailTitle.key().equals(title.key()))return;bindMetadata(title,rec,ratings,tagline,description,cast,people,extra,metaLine);});}catch(Exception ignored){runOnUiThread(()->{if(generation==version&&ApiKeys.metadataReady(this))extra.addView(ui.text("Additional metadata could not be loaded.",13,TvUi.MUTED));});}});
    }
    private void showEvent(CatalogItem e){
        detailItem=e;detailTitle=e.title;shell("Event",true);if(Library.https(e.title.background))Artwork.load(backdrop,e.title.background);
        body.addView(ui.action("‹  Back",false,this::back),new LinearLayout.LayoutParams(-2,ui.dp(38)));ui.gap(body,26);
        TextView kicker=ui.text(join(e.sport,e.league).toUpperCase(Locale.ROOT),11,TvUi.ACCENT);kicker.setLetterSpacing(.18f);body.addView(kicker);ui.gap(body,12);
        body.addView(ui.bold(e.title.name,34));ui.gap(body,12);
        body.addView(ui.text(join(CatalogViews.time(e.startsAt),SportsSource.statusLabel(e)),16,e.live()?0xffff6b6b:TvUi.WHITE));ui.gap(body,16);
        body.addView(ui.text(e.title.description,15,TvUi.MUTED));ui.gap(body,22);
        body.addView(ui.text("Saandio has no broadcast rights for this event, so it cannot be played here. Check the official broadcaster or league for legal viewing options.",14,TvUi.WHITE));ui.gap(body,12);
        body.addView(ui.text("Schedule data: TheSportsDB. Status is shown exactly as the schedule source reports it.",11,TvUi.MUTED));
        body.getChildAt(0).requestFocus();
    }
    private void findStreams(Library.Title title){
        if(title.type.equals("series")){showEpisodes(title,-1);return;}
        TextView status=ui.text("Finding playable sources…",17,TvUi.WHITE);status.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));AlertDialog progress=new AlertDialog.Builder(this).setTitle(title.name).setView(status).setNegativeButton("Cancel",null).create();progress.show();
        repository.io.execute(()->{try{List<ProviderRepository.Stream> streams=repository.streams(title);runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();String[] names=new String[streams.size()];for(int i=0;i<names.length;i++)names[i]=PlaybackPolicy.sourceChoice(streams.get(i).name);new AlertDialog.Builder(this).setTitle("Choose a source").setItems(names,(d,w)->play(title,streams.get(w))).setNegativeButton("Back",null).show();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();new AlertDialog.Builder(this).setTitle("Source unavailable").setMessage(ProviderRepository.message(e)).setPositiveButton("Back to title",null).show();});}});
    }
    private void play(Library.Title title,ProviderRepository.Stream stream){
        VpnController vpn=VpnController.get(this);VpnHealth.State own=vpn.snapshot().state;
        boolean ownTunnelUp=own!=VpnHealth.State.OFF&&own!=VpnHealth.State.NOT_CONFIGURED&&own!=VpnHealth.State.CONSENT_REQUIRED&&own!=VpnHealth.State.FAILED;
        boolean otherVpn=!vpn.claimsProtection()&&NetworkStatus.otherVpnActive(this,ownTunnelUp);
        if(!PlaybackPolicy.allow(uiPrefs().getBoolean("vpn.required",false),vpn.claimsProtection()||otherVpn)){notice("VPN required","Playback is set to require a protected VPN connection. Current state: "+VpnHealth.label(own)+". This title will not play on the normal connection.");return;}
        TextView status=ui.text("Checking this source…",16,TvUi.WHITE);status.setPadding(ui.dp(24),ui.dp(20),ui.dp(24),ui.dp(20));AlertDialog check=new AlertDialog.Builder(this).setTitle("Preparing playback").setView(status).setNegativeButton("Cancel",null).create();check.show();
        repository.io.execute(()->{try{StreamProbe.inspect(stream);research.record(title.provider,"source_check",true);runOnUiThread(()->{if(isDestroyed()||!check.isShowing())return;check.dismiss();persist(title);Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",title.key()).putExtra("title",title.name).putExtra("url",stream.url).putExtra("mimeType",stream.mimeType).putExtra("headers",new JSONObject(stream.headers).toString()).putExtra("subtitles",stream.subtitles.toString());startActivity(intent);});}catch(Exception e){research.record(title.provider,"source_check",false);runOnUiThread(()->{if(!isDestroyed()&&check.isShowing()){check.dismiss();notice("Source unavailable",ProviderRepository.message(e));}});}});
    }
    private void showEpisodes(Library.Title title,int season){openEpisodes(title,season,1,true);}
    private void openEpisodes(Library.Title title,int season,int page,boolean remember){
        final int selected=season<0&&title.seasons.length()>0&&title.seasons.optJSONObject(0)!=null?title.seasons.optJSONObject(0).optInt("s",1):season;
        int version=generation;Nav previous=capture();Toast.makeText(this,"Loading episodes…",Toast.LENGTH_SHORT).show();
        repository.io.execute(()->{try{List<Library.Title> episodes=repository.episodes(title,selected,page);episodes.sort(Comparator.comparing((Library.Title t)->t.released).thenComparingInt(t->t.episode).reversed());runOnUiThread(()->{
            if(isDestroyed()||generation!=version)return;if(remember)history.push(previous);episodeSeries=title;episodeSeason=selected;episodePage=page;shell("Episodes",true);Artwork.load(backdrop,title.background.isEmpty()?title.poster:title.background);heading(title.name,selected<0?"Episodes · "+title.providerName:"Season "+selected+" · "+title.providerName);
            body.addView(ui.action("‹ Back",false,this::back));ui.gap(body,18);
            if(title.seasons.length()>0){LinearLayout choices=new LinearLayout(this);for(int i=0;i<title.seasons.length();i++){JSONObject s=title.seasons.optJSONObject(i);if(s==null)continue;int value=s.optInt("s",i+1);choices.addView(ui.action("Season "+value,value==selected,()->openEpisodes(title,value,1,false)));}body.addView(choices);ui.gap(body,20);}
            if(episodes.isEmpty())body.addView(ui.text("No more episodes were returned by this source.",16,TvUi.MUTED));else grid(body,episodes,true);
            ui.gap(body,20);LinearLayout paging=new LinearLayout(this);if(page>1)paging.addView(ui.action("Newer episodes",false,()->openEpisodes(title,selected,page-1,false)));if(!Library.netmirror(title.provider)&&!episodes.isEmpty())paging.addView(ui.action("Older episodes",false,()->openEpisodes(title,selected,page+1,false)));body.addView(paging);
        });}catch(Exception e){runOnUiThread(()->{if(!isDestroyed()&&generation==version)notice("Episodes unavailable",ProviderRepository.message(e));});}});
    }
    /** TMDB season browser: season, episode number and air date for every episode. Metadata only. */
    private void showTmdbEpisodes(CatalogItem show,int season,boolean remember){
        if(show==null)return;
        if(show.title.seasons.length()==0){
            Toast.makeText(this,"Loading seasons…",Toast.LENGTH_SHORT).show();int version=generation;
            catalog.seriesDetails(show,filled->{if(isDestroyed()||generation!=version)return;if(filled.title.seasons.length()==0){notice("Episodes unavailable","TMDB did not list seasons for this series.");return;}showTmdbEpisodes(filled,season,remember);});
            return;
        }
        JSONArray seasons=show.title.seasons;JSONObject last=seasons.optJSONObject(seasons.length()-1);
        int selected=season>0?season:last==null?1:last.optInt("s",1);int version=generation;Nav previous=capture();
        catalog.season(show.tmdbId,selected,show.title,eps->{
            if(isDestroyed()||generation!=version)return;if(remember)history.push(previous);
            tmdbShow=show;episodeSeason=selected;shell("TV Episodes",true);Artwork.load(backdrop,show.title.background);
            heading(show.title.name,"Season "+selected+" · "+eps.size()+" episodes · episode data from TMDB");
            body.addView(ui.action("‹ Back",false,this::back));ui.gap(body,18);
            HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);LinearLayout choices=new LinearLayout(this);hs.addView(choices);
            for(int i=0;i<seasons.length();i++){JSONObject s=seasons.optJSONObject(i);if(s==null)continue;int value=s.optInt("s",i+1);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=ui.dp(10);choices.addView(ui.action("Season "+value,value==selected,()->showTmdbEpisodes(show,value,false)),lp);}
            body.addView(hs);ui.gap(body,20);
            List<Library.Title> titles=new ArrayList<>();for(int i=eps.size()-1;i>=0;i--)titles.add(eps.get(i).title);
            if(titles.isEmpty())body.addView(ui.text("TMDB lists no episodes for this season yet.",16,TvUi.MUTED));else grid(body,titles,true);
        },err->{if(!isDestroyed()&&generation==version)notice("Episodes unavailable",err);});
    }

    // ---- personal, search ---------------------------------------------------------------------------
    private void showWatchlist(){
        shell("Watchlist",false);heading("Your watchlist","Everything you saved, in one place.");
        LinearLayout target=body;int version=generation;TextView wait=ui.text("Loading…",14,TvUi.MUTED);target.addView(wait);
        catalog.watchlist(items->{if(generation!=version)return;target.removeView(wait);
            if(items.isEmpty())target.addView(ui.text("Open a title and choose + Watchlist to save it here.",16,TvUi.MUTED));
            else{List<Library.Title> titles=new ArrayList<>();for(CatalogItem c:items)titles.add(c.title);grid(target,titles);}});
    }
    private void showSearch(){
        shell("Search",false);heading("Find your next watch","Searches the synced catalog and TMDB as you type. Add-on sources can be searched with the button below.");
        EditText input=ui.input("Title, cast or genre",searchQuery);body.addView(input);ui.gap(body,12);
        LinearLayout tools=new LinearLayout(this);TextView status=ui.text("",12,TvUi.MUTED);LinearLayout results=ui.column();
        Runnable remember=()->{String q=input.getText().toString().trim();if(q.isEmpty())return;try{JSONArray old=new JSONArray(uiPrefs().getString("search.recent","[]")),next=new JSONArray();next.put(q);for(int i=0;i<old.length()&&next.length()<12;i++)if(!q.equalsIgnoreCase(old.optString(i)))next.put(old.get(i));uiPrefs().edit().putString("search.recent",next.toString()).apply();}catch(JSONException ignored){}};
        Runnable local=()->{results.removeAllViews();String q=input.getText().toString().trim();
            if(q.isEmpty()){try{JSONArray recent=new JSONArray(uiPrefs().getString("search.recent","[]"));if(recent.length()>0){results.addView(ui.bold("Recent searches",16));ui.gap(results,10);LinearLayout row=new LinearLayout(this);for(int i=0;i<recent.length();i++){String item=recent.optString(i);row.addView(ui.action(item,false,()->{input.setText(item);searchQuery=item;}));}results.addView(row);ui.gap(results,18);}}catch(JSONException ignored){}status.setText("Type or speak a title");return;}
            status.setText("Searching…");int version=generation;String lower=q.toLowerCase(Locale.ROOT);
            List<Library.Title> addonHits=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(ProviderRepository.Catalog c:catalogs.values())for(Library.Title t:c.titles)if(t.name.toLowerCase(Locale.ROOT).contains(lower)&&seen.add(t.key()))addonHits.add(t);
            catalog.search(q,found->{if(generation!=version||!q.equals(input.getText().toString().trim()))return;addResults(results,"In your catalogs",found);if(!addonHits.isEmpty())shelf(results,"From add-on catalogs",addonHits,true);status.setText(found.size()+addonHits.size()+" matches saved on this device");},
                remote->{if(generation!=version||!q.equals(input.getText().toString().trim()))return;addResults(results,"From TMDB",remote);});
        };
        Runnable addonSearch=()->{String query=input.getText().toString().trim();if(query.isEmpty())return;remember.run();status.setText("Searching add-on sources…");int version=generation;for(Library.Addon a:Library.addons())if(repository.enabled(a.id))repository.io.execute(()->{try{ProviderRepository.Catalog found=repository.load(a,query,1);runOnUiThread(()->{if(generation!=version)return;if(!found.titles.isEmpty())shelf(results,a.name,CatalogSort.newestFirst(found.titles),true);status.setText("Add-on search results");});}catch(Exception ignored){runOnUiThread(()->{if(generation==version)status.setText("Search complete for available sources");});}});};
        tools.addView(ui.action("Search add-on sources",true,addonSearch));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=ui.dp(10);
        tools.addView(ui.action("Voice search",false,()->{try{startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Search Saandio TV"),40);}catch(Exception e){notice("Voice search unavailable","This device has no speech recognizer.");}}),gap);
        body.addView(tools);ui.gap(body,14);body.addView(status);ui.gap(body,16);body.addView(results);
        searchTask=local;
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,id,event)->{remember.run();main.removeCallbacks(local);local.run();return true;});
        input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){searchQuery=s.toString();main.removeCallbacks(local);main.postDelayed(local,300);}public void afterTextChanged(Editable e){}});
        local.run();
    }
    private void addResults(LinearLayout results,String label,List<CatalogItem> items){
        if(items.isEmpty())return;Map<String,List<Library.Title>> groups=new LinkedHashMap<>();
        for(CatalogItem c:items){String g=CatalogItem.CHANNEL.equals(c.kind)?"Live channels":CatalogItem.EVENT.equals(c.kind)?"Sports":CatalogItem.SERIES.equals(c.kind)?"Series":"Movies";groups.computeIfAbsent(g,k->new ArrayList<>()).add(c.title);}
        for(Map.Entry<String,List<Library.Title>> e:groups.entrySet())shelf(results,label+" · "+e.getKey(),e.getValue(),e.getKey().equals("Live channels")||e.getKey().equals("Sports"));
    }

    // ---- settings ------------------------------------------------------------------------------------
    private void showAddons(){
        shell("Add-ons",false);heading("Your sources","Optional add-on catalogs and authorized partners. The six main tabs use the synced catalog sources listed under Settings → Catalog sync.");
        for(Library.Addon a:Library.addons()){
            LinearLayout panel=ui.column();panel.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16));panel.setBackground(ui.shape(0xff151b26,0,12));
            LinearLayout line=new LinearLayout(this);line.addView(ui.bold(a.name,21),new LinearLayout.LayoutParams(0,-2,1));ProviderRepository.Catalog cat=catalogs.get(a.id);
            String state=!repository.enabled(a.id)?"Disabled":cat!=null?cat.total+" listed items":"Not loaded";TextView count=ui.text(state,12,cat!=null?0xff94c8a3:TvUi.MUTED);addonCount.put(a.id,count);line.addView(count);panel.addView(line);ui.gap(panel,8);TextView info=ui.text(states.getOrDefault(a.id,a.description),12,TvUi.MUTED);info.setMaxLines(2);addonStatus.put(a.id,info);panel.addView(info);ui.gap(panel,14);
            LinearLayout actions=new LinearLayout(this);actions.addView(ui.action("Browse",true,()->openProvider(a)));LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.setMargins(ui.dp(10),0,0,0);actions.addView(ui.action("Configure",false,()->configure(a)),margin);
            actions.addView(ui.action(repository.enabled(a.id)?"Disable":"Enable",false,()->{prefs.edit().putBoolean("addon."+a.id,!repository.enabled(a.id)).apply();if(a.id.equals(EinthusanSimulatedProvider.ID))catalog.refresh(HdCatalogSource.ID);if(a.id.equals("news"))catalog.refresh(LiveTvSource.ID);showAddons();if(repository.enabled(a.id))load(a,1);}),margin);panel.addView(actions);body.addView(panel);ui.gap(body,14);
        }
    }
    private void configure(Library.Addon addon){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(14),ui.dp(24),ui.dp(10));box.addView(ui.text("Connect an HTTPS catalog and stream provider using its manifest URL.",14,TvUi.MUTED));ui.gap(box,12);EditText endpoint=ui.input("https://your-provider/manifest.json",repository.endpoint(addon.id));box.addView(endpoint);
        if(Library.netmirror(addon.id)){ui.gap(box,14);box.addView(ui.action("NetMirror API settings",false,this::netmirrorSettings));}
        new AlertDialog.Builder(this).setTitle(addon.name).setView(box).setPositiveButton("Save",(d,w)->{try{repository.configure(addon.id,endpoint.getText().toString().trim());states.put(addon.id,"Refreshing…");load(addon,1);}catch(Exception e){notice("Invalid provider",ProviderRepository.message(e));}}).setNegativeButton("Cancel",null).show();
    }
    private void netmirrorSettings(){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(10));box.addView(ui.text("Uses the API format found in the repository you supplied. Server availability and valid sessions are controlled by NetMirror.",13,TvUi.MUTED));ui.gap(box,12);
        String oldBase=prefs.getString("netmirror.api","https://net50.cc/mobile");SessionVault vault=new SessionVault(prefs);EditText api=ui.input("API base URL",oldBase);box.addView(api);ui.gap(box,10);EditText session=ui.input("Source-issued session (stored encrypted)",vault.read(oldBase));session.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);box.addView(session);ui.gap(box,10);
        new AlertDialog.Builder(this).setTitle("NetMirror connection · shared by three add-ons").setView(box).setPositiveButton("Save",(d,w)->{String base=api.getText().toString().trim();try{boolean changed=!SessionVault.origin(oldBase).equals(SessionVault.origin(base));vault.write(base,changed?"":session.getText().toString().trim());prefs.edit().putString("netmirror.api",base).apply();if(changed)notice("Server changed","The previous session was cleared. Add a session issued for this server.");for(Library.Addon a:Library.addons())if(Library.netmirror(a.id))load(a,1);}catch(Exception e){notice("Connection not saved",ProviderRepository.message(e));}}).setNegativeButton("Cancel",null).show();
    }
    private void showSettings(){
        shell("Settings",false);heading("Make yourself at home","Saandio TV 0.4");
        android.content.SharedPreferences appearance=uiPrefs();boolean reduced=appearance.getBoolean("reduce_motion",TvTheme.constrained(this));
        body.addView(ui.action("Reduce motion / low-end device · "+(reduced?"On":"Off")+(appearance.contains("reduce_motion")?"":" (automatic)"),false,()->{appearance.edit().putBoolean("reduce_motion",!reduced).apply();TvTheme.invalidate();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Autoplay trailers · "+(appearance.getBoolean("trailer.autoplay",true)?"On":"Off")+(reduced?" (paused while reduce motion is on)":""),false,()->{appearance.edit().putBoolean("trailer.autoplay",!appearance.getBoolean("trailer.autoplay",true)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Trailer sound · "+(appearance.getBoolean("trailer.sound",false)?"On":"Off"),false,()->{appearance.edit().putBoolean("trailer.sound",!appearance.getBoolean("trailer.sound",false)).apply();showSettings();}));ui.gap(body,14);
        int delay=appearance.getInt("trailer.delay",1200);body.addView(ui.action("Trailer delay · "+delay+" ms",false,()->{int next=delay==800?1200:delay==1200?2000:800;appearance.edit().putInt("trailer.delay",next).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Catalog sync & freshness",true,()->{history.push(capture());showSyncStatus();}));ui.gap(body,14);
        body.addView(ui.action("Add-ons & sources",false,()->{history.push(capture());showAddons();}));ui.gap(body,14);
        body.addView(ui.action("Import your media catalog",false,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),20)));ui.gap(body,14);
        body.addView(ui.action("Background audio · "+(prefs.getBoolean("background",false)?"On":"Off"),false,()->{prefs.edit().putBoolean("background",!prefs.getBoolean("background",false)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Refresh add-on sources",false,()->{refreshAddons();Toast.makeText(this,"Refreshing add-on sources",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Stop playback",false,()->{stopService(new Intent(this,PlaybackService.class));Toast.makeText(this,"Playback stopped",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Clear watch history",false,()->{android.content.SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("position.")||key.startsWith("watched_at.")||key.startsWith("duration."))edit.remove(key);edit.apply();catalog.touched(CatalogRepository.HOME_CONTINUE,CatalogRepository.TV_CONTINUE);Toast.makeText(this,"History cleared",Toast.LENGTH_SHORT).show();}));ui.gap(body,24);
        VpnHealth.State vpnState=VpnController.get(this).snapshot().state;
        body.addView(ui.action("VPN · "+(VpnHealth.claimsProtection(vpnState)?"Protected":vpnState==VpnHealth.State.NOT_CONFIGURED?(NetworkStatus.vpnActive(this)?"another VPN app is active":"not set up"):vpnState==VpnHealth.State.OFF?"Disconnected":"Not protected"),false,this::showConnection));ui.gap(body,14);
        body.addView(ui.action("Require VPN before playback · "+(appearance.getBoolean("vpn.required",false)?"On":"Off"),false,()->{appearance.edit().putBoolean("vpn.required",!appearance.getBoolean("vpn.required",false)).apply();showSettings();}));ui.gap(body,14);
        TmdbRoute route=ApiKeys.tmdbRoute(this);
        body.addView(ui.text("Movie & TV catalog · "+(route==null?"open data (Wikidata, TVMaze)":"TMDB via "+route.label()),13,TvUi.MUTED));ui.gap(body,10);
        body.addView(ui.action("Developer · metadata keys (optional)",false,this::editApiKeys));ui.gap(body,14);
        body.addView(ui.text("Einthusan: "+EinthusanAdapter.status().reason,13,TvUi.MUTED));ui.gap(body,14);
        body.addView(ui.action("Source health & research",false,this::showResearch));ui.gap(body,16);
        if(TesterGate.configured(BuildConfig.TESTER_PIN_HASH)){body.addView(ui.action("Security research (authorized testers)",false,()->startActivity(new Intent(this,SecurityDashboardActivity.class))));ui.gap(body,16);}
        body.addView(ui.text("Playback supports MP4, HLS and DASH, embedded audio tracks, and supplied subtitles. A source must expose playable media; website-only and protected sources can be unavailable.",14,TvUi.MUTED));
        ui.gap(body,12);body.addView(ui.text("No key is needed to use Saandio. The movie catalog service holds its TMDB credential on the server; optional developer keys stay in app-private settings and are never written to the research log.",13,TvUi.MUTED));
        ui.gap(body,12);body.addView(ui.text("Movie and TV metadata: TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB. Streaming availability: JustWatch via TMDB. Open film data: Wikidata (CC0). TV schedules: TVMaze (CC BY-SA 4.0). Sports schedules: TheSportsDB. Live channel directory: iptv-org.",12,TvUi.MUTED));
    }
    private void showSyncStatus(){
        shell("Sync",false);heading("Catalog sync","The saved catalog appears instantly; each source then refreshes in the background on launch, when you return to the app, and every few minutes while it is open. A failing source keeps its last catalog and retries with increasing delays.");
        LinearLayout box=ui.column();body.addView(box);int version=generation;
        catalog.sources(list->{
            if(generation!=version)return;box.removeAllViews();
            for(CatalogRepository.SourceStatus s:list){
                LinearLayout panel=ui.column();panel.setPadding(ui.dp(20),ui.dp(14),ui.dp(20),ui.dp(14));panel.setBackground(ui.shape(0xff151b26,0,12));
                panel.addView(ui.bold(s.label,19));ui.gap(panel,6);
                TextView line=ui.text(syncLine(s),13,s.ready?TvUi.MUTED:TvUi.ACCENT);line.setMaxLines(3);panel.addView(line);syncLines.put(s.id,line);ui.gap(panel,12);
                LinearLayout actions=new LinearLayout(this);LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.rightMargin=ui.dp(10);
                if(s.ready)actions.addView(ui.action("Sync now",false,()->{catalog.refresh(s.id);line.setText("Syncing now…");}),margin);
                long minutes=s.ttlMs/60_000L;
                actions.addView(ui.action("Refresh every · "+ttlLabel(minutes),false,()->{long[] presets={5,10,15,30,60,360,1440};long nextValue=presets[0];for(int i=0;i<presets.length;i++)if(presets[i]==minutes){nextValue=presets[(i+1)%presets.length];break;}catalog.setTtlMinutes(s.id,nextValue);refocusTag="";showSyncStatus();}),margin);
                panel.addView(actions);box.addView(panel);ui.gap(box,12);
            }
        });
        ui.gap(body,8);body.addView(ui.action("Sync all sources now",true,()->{catalog.refreshAll();Toast.makeText(this,"Syncing all sources",Toast.LENGTH_SHORT).show();}));ui.gap(body,12);
        body.addView(ui.action("Back to settings",false,this::back));
    }
    private void updateSyncLines(){
        int version=generation;catalog.sources(list->{if(generation!=version)return;for(CatalogRepository.SourceStatus s:list){TextView line=syncLines.get(s.id);if(line!=null)line.setText(syncLine(s));}});
    }
    private static String syncLine(CatalogRepository.SourceStatus s){
        if(!s.ready)return s.reason;
        return (s.running?"Syncing now · ":"")+s.freshness+(s.items>0?" · "+s.items+" items":"")+" · refresh every "+ttlLabel(s.ttlMs/60_000L);
    }
    private static String ttlLabel(long minutes){return minutes>=1440?(minutes/1440)+" day":minutes>=60?(minutes/60)+" h":minutes+" min";}
    private void showConnection(){
        shell("Settings",false);heading("VPN","Saandio connects only to a WireGuard server you import from a provider whose terms allow it. No servers or accounts are bundled. Status below comes from the live tunnel.");
        VpnController vpn=VpnController.get(this);
        TextView status=ui.bold("",22);body.addView(status);ui.gap(body,8);
        TextView details=ui.text("",14,TvUi.MUTED);body.addView(details);ui.gap(body,8);
        TextView warnings=ui.text("",13,0xffe7b75f);body.addView(warnings);ui.gap(body,16);
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.setMargins(0,0,ui.dp(10),0);
        LinearLayout primary=new LinearLayout(this);
        TextView toggle=ui.action("Connect",true,()->{
            VpnHealth.State s=vpn.snapshot().state;
            if(s==VpnHealth.State.OFF||s==VpnHealth.State.FAILED||s==VpnHealth.State.CONSENT_REQUIRED){Intent consent=vpn.consentIntent();if(consent!=null)startActivityForResult(consent,50);else vpn.connect();}
            else if(s==VpnHealth.State.NOT_CONFIGURED)notice("No configuration","Import a WireGuard configuration from your VPN provider first.");
            else vpn.disconnect();
        });
        primary.addView(toggle,margin);
        primary.addView(ui.action("Measure latency",false,vpn::measure),margin);
        primary.addView(ui.action("Check exit address",false,()->repository.io.execute(()->{try{String bodyText=ProviderRepository.request("https://cloudflare.com/cdn-cgi/trace","").body;String ip="",loc="",colo="";for(String line:bodyText.split("\\n")){if(line.startsWith("ip="))ip=line.substring(3);if(line.startsWith("loc="))loc=line.substring(4);if(line.startsWith("colo="))colo=line.substring(5);}boolean prot=vpn.claimsProtection();String message=(ip.isEmpty()?"No address returned.":"Exit address "+ip+(loc.isEmpty()?"":" · country "+loc)+(colo.isEmpty()?"":" · edge "+colo))+(prot?". Measured through the active tunnel.":". The tunnel is not protecting traffic right now, so this is your normal connection.");runOnUiThread(()->{if(!isDestroyed())notice("Exit address",message);});}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Check failed","The address check did not complete.");});}})),margin);
        body.addView(primary);ui.gap(body,12);
        LinearLayout config=new LinearLayout(this);
        config.addView(ui.action("Import .conf file",false,()->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),51);}catch(Exception e){notice("File picker unavailable","This device has no document picker. Use Paste configuration instead.");}}),margin);
        config.addView(ui.action("Paste configuration",false,()->pasteVpnConfig("")),margin);
        config.addView(ui.action("Remove configuration",false,()->new AlertDialog.Builder(this).setTitle("Remove VPN configuration?").setMessage("The tunnel is disconnected and the encrypted configuration and its key are deleted from this device.").setPositiveButton("Remove",(d,w)->vpn.removeConfig()).setNegativeButton("Cancel",null).show()),margin);
        body.addView(config);ui.gap(body,12);
        LinearLayout options=new LinearLayout(this);
        options.addView(ui.action("IPv6 leak protection · "+(vpn.store().leakProtection()?"On":"Off"),false,()->{vpn.store().setLeakProtection(!vpn.store().leakProtection());showConnection();}),margin);
        options.addView(ui.action("Auto-reconnect · "+(vpn.store().autoReconnect()?"On":"Off"),false,()->{vpn.store().setAutoReconnect(!vpn.store().autoReconnect());showConnection();}),margin);
        options.addView(ui.action("Android VPN settings (always-on, kill switch)",false,()->{try{startActivity(new Intent(android.provider.Settings.ACTION_VPN_SETTINGS));}catch(Exception e){notice("VPN settings unavailable","This device did not open system VPN settings.");}}),margin);
        body.addView(options);ui.gap(body,16);
        body.addView(ui.text("Kill switch: Android enforces it only when you set Saandio TV as the always-on VPN and turn on \"Block connections without VPN\" in Android VPN settings. Inside the app, \"Require VPN before playback\" also pauses playback when protection drops. IPv6 leak protection routes all IPv6 into the tunnel so it cannot use your normal connection. The tunnel does not change which titles a provider licenses for your region.",13,TvUi.MUTED));ui.gap(body,14);
        body.addView(ui.action("Back to settings",false,this::showSettings));
        Runnable render=()->{
            VpnController.Snapshot s=vpn.snapshot();
            status.setText(VpnHealth.label(s.state));status.setTextColor(VpnHealth.claimsProtection(s.state)?0xff94c8a3:TvUi.WHITE);
            boolean live=s.state!=VpnHealth.State.OFF&&s.state!=VpnHealth.State.FAILED&&s.state!=VpnHealth.State.NOT_CONFIGURED&&s.state!=VpnHealth.State.CONSENT_REQUIRED;
            toggle.setText(live?"Disconnect":"Connect");
            List<String> lines=new ArrayList<>();
            if(!s.label.isEmpty()||!s.endpoint.isEmpty())lines.add("Server: "+join(s.label,s.endpoint));
            if(s.lastHandshakeMs>0)lines.add("Last handshake: "+Math.max(0,(System.currentTimeMillis()-s.lastHandshakeMs)/1000)+" s ago");
            if(live)lines.add("Traffic: ↓ "+bytes(s.rxBytes)+" ("+bytes(s.rxRate)+"/s)  ↑ "+bytes(s.txBytes)+" ("+bytes(s.txRate)+"/s)");
            if(s.latencyMs>=0)lines.add("Latency: "+s.latencyMs+" ms (TCP connect through the tunnel to its DNS server)");else if(live)lines.add("Latency: not measured · needs a working tunnel");
            if(s.connectMs>=0)lines.add("Tunnel setup took "+s.connectMs+" ms (server handshake not included)");
            if(s.analysis!=null)lines.add("DNS: "+(s.analysis.dns.isEmpty()?"not set":String.join(", ",s.analysis.dns))+" · IPv4 "+(s.analysis.fullV4?"full tunnel":"split")+" · IPv6 "+(s.analysis.fullV6?"tunnelled":vpn.store().leakProtection()?"blocked outside tunnel":"not protected"));
            if(s.alwaysOn!=null)lines.add("Android always-on: "+(s.alwaysOn?"yes":"no")+" · Block connections without VPN: "+(Boolean.TRUE.equals(s.lockdown)?"yes":"no"));
            if(s.reconnects>0)lines.add("Reconnect attempts: "+s.reconnects);
            if(!s.error.isEmpty()&&!VpnHealth.claimsProtection(s.state))lines.add(s.error);
            details.setText(String.join("\n",lines));
            List<String> warn=new ArrayList<>();if(s.analysis!=null)for(String w:s.analysis.warnings)if(!(w.startsWith("IPv6")&&vpn.store().leakProtection()))warn.add("⚠ "+w);
            warnings.setText(String.join("\n",warn));warnings.setVisibility(warn.isEmpty()?View.GONE:View.VISIBLE);
        };
        render.run();
        vpnListener=()->runOnUiThread(()->{if(screen.equals("Settings")&&status.isAttachedToWindow())render.run();});
        vpn.addListener(vpnListener);
    }
    private static String bytes(long n){if(n<1024)return n+" B";if(n<1048576)return String.format(Locale.US,"%.1f KB",n/1024.0);if(n<1073741824)return String.format(Locale.US,"%.1f MB",n/1048576.0);return String.format(Locale.US,"%.2f GB",n/1073741824.0);}
    private void pasteVpnConfig(String initial){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(10));
        box.addView(ui.text("Paste the WireGuard configuration from your VPN provider. It is encrypted with a key that never leaves this device. Configurations with shell hooks are refused.",13,TvUi.MUTED));ui.gap(box,10);
        EditText label=ui.input("Name (for example: Home, Frankfurt)","");box.addView(label);ui.gap(box,8);
        EditText text=ui.input("[Interface] …",initial);text.setSingleLine(false);text.setMinLines(6);text.setMaxLines(12);box.addView(text);
        new AlertDialog.Builder(this).setTitle("WireGuard configuration").setView(box).setPositiveButton("Save",(d,w)->saveVpnConfig(text.getText().toString(),label.getText().toString())).setNegativeButton("Cancel",null).show();
    }
    private void saveVpnConfig(String text,String label){
        try{
            VpnController vpn=VpnController.get(this);vpn.importConfig(text,label);VpnConfigAnalysis a=VpnConfigAnalysis.of(text);
            List<String> notes=new ArrayList<>(a.warnings);
            if(!a.fullV6&&vpn.store().leakProtection())notes.replaceAll(w->w.startsWith("IPv6")?"IPv6 is not routed by this configuration; IPv6 leak protection (on) blocks it outside the tunnel.":w);
            notice("Configuration saved",notes.isEmpty()?"Ready to connect.":String.join("\n",notes));showConnection();
        }
        catch(IOException e){notice("Configuration not saved",e.getMessage()==null?"The configuration could not be saved.":e.getMessage());}
    }
    private void editApiKeys(){
        LinearLayout box=ui.column();box.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(10));
        box.addView(ui.text("Optional, for developers testing their own accounts. Viewers do not need any key: the catalog loads from the Saandio catalog service or open data. A personal TMDB key here calls TMDB directly from this device. OMDb adds IMDb / Rotten Tomatoes / Metacritic on details pages. Trakt is optional. YouTube Data is used only for trailer search. TheSportsDB's free key \"123\" is used for schedules unless you add your own.",13,TvUi.MUTED));ui.gap(box,12);
        EditText tmdb=ui.input("TMDB API key",ApiKeys.tmdb(this));box.addView(tmdb);ui.gap(box,8);
        EditText omdb=ui.input("OMDb API key",ApiKeys.omdb(this));box.addView(omdb);ui.gap(box,8);
        EditText trakt=ui.input("Trakt client ID",ApiKeys.trakt(this));box.addView(trakt);ui.gap(box,8);
        EditText youtube=ui.input("YouTube Data API key",ApiKeys.youtube(this));box.addView(youtube);ui.gap(box,8);
        String sportsKey=ApiKeys.sportsDb(this);EditText sports=ui.input("TheSportsDB key (blank = free key)",sportsKey.equals(ApiKeys.SPORTSDB_FREE)?"":sportsKey);box.addView(sports);
        new AlertDialog.Builder(this).setTitle("Developer metadata keys").setView(box).setPositiveButton("Save",(d,w)->{
            boolean tmdbChanged=!tmdb.getText().toString().trim().equals(ApiKeys.tmdb(this));
            ApiKeys.save(this,tmdb.getText().toString(),omdb.getText().toString(),trakt.getText().toString(),youtube.getText().toString());ApiKeys.saveSportsDb(this,sports.getText().toString());
            if(tmdbChanged){catalog.refresh(TmdbCatalogSource.MOVIES);catalog.refresh(TmdbCatalogSource.TV);}catalog.refresh(SportsSource.ID);
            if(screen.equals("Settings"))showSettings();else openTab(tab,false);
        }).setNegativeButton("Cancel",null).show();
    }
    private void review(Library.Title title){
        EditText text=ui.input("What did you think?",prefs.getString("review."+title.key(),""));text.setSingleLine(false);text.setMinLines(3);
        new AlertDialog.Builder(this).setTitle("Your review · "+title.name).setView(text).setPositiveButton("Save",(d,w)->{prefs.edit().putString("review."+title.key(),text.getText().toString()).apply();persist(title);showDetails(title);}).setNegativeButton("Cancel",null).show();
    }
    private void showResearch(){
        shell("Research",false);heading("Source health","Availability checks are observations, not a guarantee of rights, uptime or decoding.");
        for(Library.Addon a:Library.addons()){body.addView(ui.bold(a.name,19));ui.gap(body,6);body.addView(ui.text(states.getOrDefault(a.id,"Not checked this session"),13,TvUi.MUTED));ui.gap(body,17);}
        body.addView(ui.action("Export redacted research log",true,()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"astra-research.json"),30)));ui.gap(body,14);
        body.addView(ui.action("Clear research log",false,()->{prefs.edit().remove("research.events").apply();Toast.makeText(this,"Research log cleared",Toast.LENGTH_SHORT).show();}));
    }

    // ---- metadata ------------------------------------------------------------------------------------
    private void bindMetadata(Library.Title title,org.json.JSONObject rec,LinearLayout ratings,TextView tagline,TextView description,TextView cast,LinearLayout people,LinearLayout extra,TextView metaLine){
        extra.removeAllViews();ratings.removeAllViews();
        if(rec==null||!ApiKeys.metadataReady(this)){if(ApiKeys.metadataReady(this))extra.addView(ui.text("Additional metadata is unavailable for this title.",13,TvUi.MUTED));return;}
        if(rec.optString("status").equals("unmatched")||rec.optDouble("confidence")<MetadataMatcher.AUTO){extra.addView(ui.text("No confident metadata match. Use Correct metadata match if this title is wrong.",13,TvUi.MUTED));return;}
        String backdropUrl=rec.optString("backdrop");if(backdropUrl.isEmpty())backdropUrl=rec.optString("poster");final String art=backdropUrl;if(!art.isEmpty())Artwork.load(backdrop,art,bitmap->tintHero(ColorExtractor.extract(art,bitmap)));
        metaLine.setText(join(yearOf(rec.optString("year")),rec.optString("runtime").isEmpty()?"":rec.optInt("runtime")+" min",rec.optString("age"),rec.optString("genres"),rec.optString("languages")));
        if(!rec.optString("tagline").isEmpty()){tagline.setVisibility(View.VISIBLE);tagline.setText(rec.optString("tagline"));}
        if(title.description.isEmpty()&&!rec.optString("overview").isEmpty())description.setText(rec.optString("overview"));
        else if(!rec.optString("overview").isEmpty()&&rec.optString("overview").length()>title.description.length()){description.setMaxLines(3);description.setEllipsize(TextUtils.TruncateAt.END);description.setText(rec.optString("overview"));description.setFocusable(true);description.setOnClickListener(v->{description.setMaxLines(Integer.MAX_VALUE);description.setEllipsize(null);});}
        badge(ratings,"TMDB",rec.optString("tmdbScore"));badge(ratings,"IMDb",rec.optString("imdbScore"));badge(ratings,"Rotten Tomatoes",rec.optString("rtScore"));badge(ratings,"Metacritic",rec.optString("metaScore"));badge(ratings,"Trakt",rec.optString("traktScore"));
        JSONArray tmdbCast=rec.optJSONArray("cast");
        if(tmdbCast!=null&&tmdbCast.length()>0){cast.setText("Focus a person for filmography.");people.setVisibility(View.VISIBLE);HorizontalScrollView scroller=new HorizontalScrollView(this);scroller.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);scroller.addView(row);people.addView(scroller);
            for(int i=0;i<tmdbCast.length();i++){JSONObject p=tmdbCast.optJSONObject(i);if(p==null)continue;LinearLayout cell=ui.column();cell.setPadding(ui.dp(6),ui.dp(6),ui.dp(6),ui.dp(6));ImageView photo=new ImageView(this);photo.setScaleType(ImageView.ScaleType.CENTER_CROP);TvTheme.round(photo,ui.dp(36));cell.addView(photo,new LinearLayout.LayoutParams(ui.dp(72),ui.dp(72)));ui.gap(cell,6);TextView pn=ui.bold(p.optString("name"),11);pn.setMaxLines(1);cell.addView(pn);TextView ch=ui.text(p.optString("character"),10,TvUi.MUTED);ch.setMaxLines(1);cell.addView(ch);Artwork.load(photo,p.optString("photo"));String pid=Integer.toString(p.optInt("id"));String pname=p.optString("name");ui.focusCard(cell,null);cell.setOnClickListener(v->openPerson(pid,pname));row.addView(cell);}
        }
        if(rec.optJSONArray("tmdbReviews")!=null){extra.addView(ui.bold("TMDB reviews",17));ui.gap(extra,8);JSONArray reviews=rec.optJSONArray("tmdbReviews");for(int i=0;i<reviews.length();i++){JSONObject r=reviews.optJSONObject(i);if(r==null)continue;extra.addView(ui.text(r.optString("author"),12,TvUi.ACCENT));ui.gap(extra,4);TextView bodyText=ui.text(r.optString("content"),13,TvUi.MUTED);bodyText.setMaxLines(4);extra.addView(bodyText);ui.gap(extra,12);}}
        if(!rec.optString("collection").isEmpty()){extra.addView(ui.bold("Collection",17));ui.gap(extra,6);extra.addView(ui.text(rec.optString("collection"),14,TvUi.WHITE));ui.gap(extra,14);}
        JSONArray similar=rec.optJSONArray("similar");
        if(similar!=null&&similar.length()>0){List<String> names=new ArrayList<>();for(int i=0;i<similar.length();i++){JSONObject s=similar.optJSONObject(i);if(s!=null)names.add(s.optString("name"));}int version=generation;
            findCatalog(names,related->{if(generation!=version||related.isEmpty())return;shelf(extra,"Similar titles in your catalogs",related,false);});}
    }
    private void badge(LinearLayout row,String source,String value){
        if(value==null||value.isEmpty())return;
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.rightMargin=ui.dp(8);
        LinearLayout chip=ui.column();chip.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));chip.setBackground(ui.shape(0x5524252b,0,10));
        chip.addView(ui.text(source,9,TvUi.MUTED));chip.addView(ui.bold(value,14));row.addView(chip,gap);
    }
    private String yearOf(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("((?:19|20)\\d{2})").matcher(value==null?"":value);return m.find()?m.group(1):value;}
    /** Looks names up in the synced store and saved add-on catalogs off the main thread. */
    private void findCatalog(List<String> names,java.util.function.Consumer<List<Library.Title>> callback){
        List<Library.Title> addonTitles=new ArrayList<>();for(ProviderRepository.Catalog c:catalogs.values())addonTitles.addAll(c.titles);
        catalog.background(()->{
            List<Library.Title> out=new ArrayList<>();Set<String> seen=new HashSet<>();
            for(String name:names){
                String n=MetadataMatcher.normalize(name);if(n.isEmpty())continue;Library.Title hit=null;
                for(CatalogItem c:catalog.store.search(name,5))if(MetadataMatcher.normalize(c.title.name).equals(n)){hit=c.title;break;}
                if(hit==null)for(Library.Title t:addonTitles)if(MetadataMatcher.normalize(t.name).equals(n)){hit=t;break;}
                if(hit!=null&&seen.add(hit.key()))out.add(hit);
            }
            runOnUiThread(()->{if(!isDestroyed())callback.accept(out);});
        });
    }
    private void openPerson(String id,String name){Nav n=capture();n.personId=id;n.personName=name;history.push(n);showPerson(id,name,false);}
    private void showPerson(String id,String name,boolean remember){
        shell("Person",true);heading(name,"Filmography from TMDB. Titles open from your synced catalogs.");
        body.addView(ui.action("‹ Back",false,this::back));ui.gap(body,16);LinearLayout list=ui.column();body.addView(list);list.addView(new ShimmerView(this),new LinearLayout.LayoutParams(-1,ui.dp(100)));
        int version=generation;repository.io.execute(()->{try{JSONObject person=metadata.person(id);JSONArray credits=metadata.filmography(id);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;list.removeAllViews();Artwork.load(backdrop,person.optString("profile_path").isEmpty()?"":"https://image.tmdb.org/t/p/w780"+person.optString("profile_path"));
            if(!person.optString("biography").isEmpty()){TextView bio=ui.text(person.optString("biography"),14,TvUi.MUTED);bio.setMaxLines(6);list.addView(bio);ui.gap(list,16);}
            List<String> names=new ArrayList<>();for(int i=0;i<Math.min(credits.length(),40);i++){JSONObject c=credits.optJSONObject(i);if(c!=null)names.add(c.optString("title",c.optString("name")));}
            findCatalog(names,found->{if(generation!=version)return;if(found.isEmpty())list.addView(ui.text("None of this person's titles are in your synced catalogs yet.",14,TvUi.MUTED));else shelf(list,"In your catalogs",found,false);});
        });}catch(Exception e){runOnUiThread(()->{if(generation==version)notice("Filmography unavailable","TMDB did not return this person.");});}});
    }
    private void correctMatch(Library.Title title){
        if(!ApiKeys.metadataReady(this)){notice("Matching unavailable","This build has no movie catalog service configured, so titles cannot be re-matched.");return;}
        EditText q=ui.input("Search TMDB",title.name);
        new AlertDialog.Builder(this).setTitle("Correct match").setView(q).setPositiveButton("Search",(d,w)->repository.io.execute(()->{try{
            Library.Title probe=Library.Title.from(title.json());probe.name=q.getText().toString().trim();if(probe.name.isEmpty())probe.name=title.name;
            List<MetadataMatcher.Candidate> found=metadata.search(probe,MetadataMatcher.kind(title));
            runOnUiThread(()->{if(isDestroyed())return;if(found.isEmpty()){notice("No matches","TMDB returned no titles for that search.");return;}
                String[] labels=new String[found.size()];for(int i=0;i<found.size();i++){MetadataMatcher.Candidate c=found.get(i);labels[i]=c.title+" ("+c.year+") · "+String.format(Locale.US,"%.0f%%",MetadataMatcher.score(title,c)*100);}
                new AlertDialog.Builder(this).setTitle("Choose the correct title").setItems(labels,(dd,which)->repository.io.execute(()->{try{metadata.enrich(title,found.get(which).id);runOnUiThread(()->{if(!isDestroyed())showDetails(title);});}catch(Exception e){runOnUiThread(()->notice("Match not saved","TMDB details were unavailable."));}})).setNegativeButton("Cancel",null).show();
            });
        }catch(Exception e){runOnUiThread(()->notice("Search failed","TMDB could not be reached."));}})).setNegativeButton("Cancel",null).show();
    }

    // ---- legacy browse filter (add-on pages) --------------------------------------------------------
    private void showFilters(){
        if(filterRail!=null){hideFilters();return;}
        if(draftFilter==null)draftFilter=BrowseFilter.load(uiPrefs());
        attachFilter();
    }
    private void attachFilter(){
        if(frame==null)return;if(filterRail!=null)frame.removeView(filterRail);
        filterRail=FilterPanel.create(this,ui,draftFilter,this::commitFilters,this::hideFilters,this::attachFilter);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(ui.dp(400),-1,Gravity.END);frame.addView(filterRail,p);filterRail.requestFocus();
    }
    private void hideFilters(){if(filterRail!=null&&frame!=null)frame.removeView(filterRail);filterRail=null;draftFilter=null;}
    private void commitFilters(){
        if(draftFilter!=null)draftFilter.save(uiPrefs());hideFilters();
        if(screen.startsWith("Provider:")){Library.Addon a=addonFor(screen.substring(9));if(a!=null)showProvider(a,sourcePage);}
    }

    // ---- helpers ---------------------------------------------------------------------------------------
    private android.content.SharedPreferences uiPrefs(){return getSharedPreferences("astra_ui_v1",0);}
    private void heading(String title,String subtitle){ui.gap(body,18);body.addView(ui.bold(title,32));ui.gap(body,9);TextView info=ui.text(subtitle,14,TvUi.MUTED);info.setMaxLines(3);body.addView(info);ui.gap(body,24);}
    private void notice(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private Library.Addon addonFor(String id){for(Library.Addon a:Library.addons())if(a.id.equals(id))return a;return null;}
    private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<600;}
    private static String join(String...strings){List<String> out=new ArrayList<>();for(String s:strings)if(s!=null&&!s.isEmpty())out.add(s);return String.join("  ·  ",out);}
    private int position(Library.Title title){return prefs.getInt("position."+title.key(),0);}
    private void migrateLegacy(){
        List<Library.Title> old=new ArrayList<>(Library.titles(this));old.addAll(Library.samples());android.content.SharedPreferences.Editor edit=prefs.edit();Map<String,?> all=prefs.getAll();
        for(Library.Title t:old){for(String prefix:new String[]{"saved.","review.","position."}){String before=prefix+t.id,after=prefix+t.key();if(all.containsKey(before)&&!all.containsKey(after)){Object value=all.get(before);if(value instanceof Boolean)edit.putBoolean(after,(Boolean)value);else if(value instanceof Integer)edit.putInt(after,(Integer)value);else if(value instanceof String)edit.putString(after,(String)value);}}}edit.apply();
    }
    private void persist(Library.Title title){try{prefs.edit().putString("title."+title.key(),title.json().toString()).apply();}catch(JSONException ignored){}}
    private View findTagged(View view,String tag){if(tag.equals(view.getTag()))return view;if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findTagged(group.getChildAt(i),tag);if(found!=null)return found;}}return null;}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==50){if(result==RESULT_OK)VpnController.get(this).connect();else notice("VPN not started","Android VPN permission was not granted, so no tunnel was created.");return;}
        if(result!=RESULT_OK||data==null)return;
        if(request==51){repository.io.execute(()->{try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>VpnConfigAnalysis.MAX_BYTES)throw new IOException("The configuration is larger than 16 KB.");out.write(b,0,n);}
            String text=out.toString("UTF-8"),name=data.getData().getLastPathSegment()==null?"":data.getData().getLastPathSegment().replaceAll(".*[/:]","").replaceAll("\\.conf$","");
            runOnUiThread(()->{if(!isDestroyed())saveVpnConfig(text,name);});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Import failed",e.getMessage()!=null&&e.getMessage().startsWith("The ")?e.getMessage():"The file could not be read.");});}});return;}
        if(request==40){java.util.ArrayList<String> spoken=data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);if(spoken!=null&&!spoken.isEmpty()){searchQuery=spoken.get(0);showSearch();}return;}
        if(request==30){repository.io.execute(()->{try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(research.export().getBytes(java.nio.charset.StandardCharsets.UTF_8));runOnUiThread(()->Toast.makeText(this,"Redacted log exported",Toast.LENGTH_SHORT).show());}catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Export failed",ProviderRepository.message(e));});}});return;}
        if(request!=20)return;
        repository.io.execute(()->{try(InputStream in=getContentResolver().openInputStream(data.getData());ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Catalog must be under 1 MB");out.write(b,0,n);}String json=out.toString("UTF-8");int count=Library.parse(json).size();prefs.edit().putString("catalog",json).apply();runOnUiThread(()->{if(!isDestroyed()){Toast.makeText(this,"Imported "+count+" videos",Toast.LENGTH_LONG).show();showTab("Home");}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Import failed",ProviderRepository.message(e));});}});
    }
    @Override public void onBackPressed(){if(filterRail!=null){hideFilters();return;}if(!history.isEmpty()||!screen.equals("Home"))back();else super.onBackPressed();}
    @Override protected void onDestroy(){if(vpnListener!=null)VpnController.get(this).removeListener(vpnListener);generation++;main.removeCallbacksAndMessages(null);if(trailers!=null)trailers.release();repository.io.shutdownNow();catalog.removeListener(catalogListener);super.onDestroy();}
}
