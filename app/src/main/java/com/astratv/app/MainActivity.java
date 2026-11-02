package com.astratv.app;

import android.app.*;
import android.content.*;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import org.json.*;
import java.io.*;
import java.util.*;
import com.astratv.app.coreui.*;

/** Native TV shell. Every card leads to native details, episodes and source selection. */
@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
    private TvUi ui;
    private ProviderRepository repository;
    private android.content.SharedPreferences prefs;
    private FrameLayout frame;
    private LinearLayout content,body;
    private ScrollView scroll;
    private ImageView backdrop;
    private View heroShade,heroFade;
    private TextView heroTitle,heroMeta,heroDescription,heroProvider,heroWatch;
    private LinearLayout heroChips,homeShelves;
    private View filterRail;
    private BrowseFilter draftFilter;
    private Library.Title featured,detailTitle;
    private String screen="Home",filter="All",focusKey="";
    private int generation=0,homeY=0;
    private boolean heroChosen=false;
    private final Map<String,ProviderRepository.Catalog> catalogs=new LinkedHashMap<>();
    private final Map<String,ProviderRepository.Catalog> browsePages=new HashMap<>();
    private final Deque<Nav> history=new ArrayDeque<>();
    private int sourcePage=1,episodePage=1,episodeSeason=-1;
    private Library.Title episodeSeries;
    private String searchQuery="";
    private static final class Nav{String screen,query,personId,personName,selection;int page,season,epPage,y;Library.Title title,series;}
    private String browseSelection="";
    private Nav capture(){Nav n=new Nav();n.screen=screen;n.query=searchQuery;n.selection=browseSelection;n.page=sourcePage;n.title=detailTitle;n.series=episodeSeries;n.season=episodeSeason;n.epPage=episodePage;n.y=scroll==null?0:scroll.getScrollY();return n;}
    private void back(){if(filterRail!=null){hideFilters();return;}if(history.isEmpty()){showHome();return;}Nav n=history.pop();searchQuery=n.query;switch(n.screen){case "Details":showDetails(n.title);break;case "Person":showPerson(n.personId,n.personName,false);break;case "Episodes":openEpisodes(n.series,n.season,n.epPage,false);break;case "Search":showSearch();break;case "Watchlist":showWatchlist();break;case "Add-ons":showAddons();break;case "Trending":showTrending();break;case "Latest Releases":showLatest();break;case "Languages":showLanguages(n.selection);break;case "Genres":showGenres(n.selection);break;default:if(n.screen.startsWith("Provider:"))showProvider(addonFor(n.screen.substring(9)),n.page);else showHome();}if(!screen.equals("Episodes"))scroll.post(()->scroll.scrollTo(0,n.y));}
    private void openDetails(Library.Title t){history.push(capture());showDetails(t);}
    private void openProvider(Library.Addon a){history.push(capture());showProvider(a,1);}
    private final Map<String,String> states=new HashMap<>();
    private final Map<String,LinearLayout> panels=new HashMap<>();
    private final Map<String,TextView> addonStatus=new HashMap<>(),addonCount=new HashMap<>();
    private final Set<String> loading=new HashSet<>();
    private final Set<String> enriching=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private ResearchLog research;
    private MetadataRepository metadata;
    private TrailerEngine trailers;
    private FrameLayout trailerHost;
    private GlassPanel topNavigation;
    private final Map<String,String> rowFocus=new HashMap<>();
    private VpnController.Listener vpnListener;
    private CatalogIndex catalogIndex;
    private List<CatalogRanking.Trend> trendsDay=new ArrayList<>(),trendsWeek=new ArrayList<>();
    private Map<String,Integer> trendDay=new HashMap<>(),trendWeek=new HashMap<>();
    private Map<String,List<Library.Title>> alternates=new HashMap<>();
    private static final long RECENT_MS=30L*24*60*60*1000;

    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);ui=new TvUi(this);prefs=getSharedPreferences("astra",0);research=new ResearchLog(prefs);repository=new ProviderRepository(this);metadata=new MetadataRepository(this);trailers=new TrailerEngine(this,metadata,repository.io);catalogIndex=new CatalogIndex(this);migrateLegacy();loadTrending();
        for(Library.Addon addon:Library.addons()){
            List<Library.Title> cached=repository.cached(addon.id);
            if(!cached.isEmpty())catalogs.put(addon.id,new ProviderRepository.Catalog(cached,"Saved catalog",true));
        }
        showHome();refreshAll();
    }
    private void shell(String name,boolean cinematic){
        if(vpnListener!=null){VpnController.get(this).removeListener(vpnListener);vpnListener=null;}
        generation++;screen=name;panels.clear();addonStatus.clear();addonCount.clear();heroTitle=null;heroChosen=false;filterRail=null;homeShelves=null;if(trailers!=null)trailers.cancel();
        frame=new FrameLayout(this);frame.setBackgroundColor(TvUi.BG);frame.setClipChildren(false);
        if(cinematic){
            backdrop=new ImageView(this);backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);FrameLayout.LayoutParams image=new FrameLayout.LayoutParams(-1,ui.dp(540),Gravity.TOP);frame.addView(backdrop,image);
            trailerHost=new FrameLayout(this);frame.addView(trailerHost,new FrameLayout.LayoutParams(-1,ui.dp(540),Gravity.TOP));if(trailers!=null)trailers.attach(trailerHost);
            heroShade=new View(this);tintHero(TvUi.BG);frame.addView(heroShade,new FrameLayout.LayoutParams(-1,ui.dp(460)));
            heroFade=new View(this);heroFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x0008090c,0x6608090c,TvUi.BG}));frame.addView(heroFade,new FrameLayout.LayoutParams(-1,ui.dp(460)));
        }else{backdrop=null;heroShade=null;heroFade=null;trailerHost=null;if(trailers!=null)trailers.attach(null);}
        LinearLayout layout=new LinearLayout(this);layout.setClipChildren(false);frame.addView(layout,new FrameLayout.LayoutParams(-1,-1));
        content=ui.column();content.setPadding(ui.dp(42),0,ui.dp(42),0);layout.addView(content,new LinearLayout.LayoutParams(-1,-1));
        scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setClipChildren(false);scroll.setVerticalScrollBarEnabled(false);body=ui.column();body.setPadding(ui.dp(8),ui.dp(96),ui.dp(8),ui.dp(40));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        topNavigation=new GlassPanel(this);FrameLayout.LayoutParams navPosition=new FrameLayout.LayoutParams(-2,ui.dp(48),Gravity.TOP|Gravity.CENTER_HORIZONTAL);navPosition.topMargin=ui.dp(18);frame.addView(topNavigation,navPosition);
        HorizontalScrollView navScroll=new HorizontalScrollView(this);navScroll.setHorizontalScrollBarEnabled(false);LinearLayout nav=new LinearLayout(this);nav.setPadding(ui.dp(6),ui.dp(4),ui.dp(6),ui.dp(4));nav.setGravity(Gravity.CENTER);navScroll.addView(nav);topNavigation.addView(navScroll,new FrameLayout.LayoutParams(-2,-1));
        for(String target:new String[]{"Search","Home","Movies","Trending","Latest Releases","Languages","Genres","Watchlist","Shows","Live TV","Settings"}){
            boolean selected=target.equals(name)||name.equals("Add-ons")&&target.equals("Settings")||name.equals("Home")&&(target.equals("Movies")&&filter.equals("Movies")||target.equals("Shows")&&filter.equals("Shows")||target.equals("Live TV")&&filter.equals("Live"));
            if(name.equals("Home")&&!filter.equals("All")&&target.equals("Home"))selected=false;
            nav.addView(ui.navItem(target,selected,()->navigate(target)),new LinearLayout.LayoutParams(-2,-1));
        }
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{topNavigation.setElevation(ui.dp(y>16?12:0));if(backdrop!=null)topNavigation.artwork(backdrop.getDrawable());});
        setContentView(frame);if(!TvTheme.reduced(this)){body.setAlpha(0f);body.setTranslationY(ui.dp(8));body.animate().alpha(1).translationY(0).setDuration(220).start();}
    }
    private void navigate(String destination){
        history.clear();if(destination.equals("Search"))searchQuery="";
        if(screen.equals("Home"))homeY=scroll.getScrollY();
        browseSelection="";
        switch(destination){case "Trending":showTrending();break;case "Latest Releases":showLatest();break;case "Languages":showLanguages("");break;case "Genres":showGenres("");break;case "Movies":filter="Movies";homeY=0;showHome();break;case "Shows":case "TV Shows":filter="Shows";homeY=0;showHome();break;case "Live TV":filter="Live";homeY=0;showHome();break;case "Search":showSearch();break;case "Library":case "Watchlist":showWatchlist();break;case "Add-ons":showAddons();break;case "Settings":showSettings();break;default:filter="All";showHome();}
    }
    private android.content.SharedPreferences uiPrefs(){return getSharedPreferences("astra_ui_v1",0);}
    private void showHome(){
        featured=null;shell("Home",true);
        TextView wordmark=ui.text("S A A N D I O",12,TvUi.MUTED);body.addView(wordmark);ui.gap(body,18);
        LinearLayout hero=ui.column();hero.setGravity(Gravity.CENTER_VERTICAL);heroProvider=ui.text("YOUR CINEMA, CONNECTED",10,TvUi.ACCENT);heroProvider.setLetterSpacing(.2f);hero.addView(heroProvider);ui.gap(hero,10);
        heroTitle=ui.bold("Your next great watch.",compact()?32:38);heroTitle.setMaxLines(compact()?1:2);heroTitle.setEllipsize(TextUtils.TruncateAt.END);hero.addView(heroTitle);ui.gap(hero,10);
        LinearLayout metaRow=new LinearLayout(this);metaRow.setGravity(Gravity.CENTER_VERTICAL);heroMeta=ui.text("Movies  ·  Shows  ·  Live TV",13,TvUi.WHITE);metaRow.addView(heroMeta);hero.addView(metaRow);ui.gap(hero,10);
        heroChips=new LinearLayout(this);heroChips.setGravity(Gravity.CENTER_VERTICAL);hero.addView(heroChips);ui.gap(hero,10);
        heroDescription=ui.text("Browse your sources, find your next watch, and play right here.",13,TvUi.MUTED);heroDescription.setMaxLines(compact()?1:2);hero.addView(heroDescription,new LinearLayout.LayoutParams(ui.dp(430),-2));ui.gap(hero,16);
        LinearLayout heroActions=new LinearLayout(this);heroActions.setClipChildren(false);
        LinearLayout.LayoutParams actionGap=new LinearLayout.LayoutParams(-2,-2);actionGap.leftMargin=ui.dp(10);
        heroActions.addView(ui.action("Play",true,()->{if(featured!=null)findStreams(featured);else showAddons();}));
        heroWatch=ui.action("+  Watchlist",false,()->{if(featured!=null)toggleWatchlist(featured);});heroActions.addView(heroWatch,actionGap);
        heroActions.addView(ui.action("More Info",false,()->{if(featured!=null)openDetails(featured);else showAddons();}),actionGap);
        hero.addView(heroActions);body.addView(hero,new LinearLayout.LayoutParams(-1,ui.dp(compact()?210:300)));
        LinearLayout tools=new LinearLayout(this);BrowseFilter current=BrowseFilter.load(uiPrefs());
        tools.addView(ui.action(current.active()?"Filters · On":"Filters",false,this::showFilters));
        tools.addView(ui.action("Sort · "+sortLabel(current.sort),false,this::showFilters),actionGap);
        body.addView(tools);ui.gap(body,18);
        homeShelves=ui.column();body.addView(homeShelves);renderHomeShelves();
        if(featured!=null)setHero(featured);
        scroll.post(()->{scroll.scrollTo(0,homeY);if(!focusKey.isEmpty()){View found=findTagged(body,focusKey);if(found!=null)found.requestFocus();}});
    }
    private String sortLabel(String sort){return CatalogSort.label(sort);}
    private boolean categoryMatches(String id){return filter.equals("All")||filter.equals("Movies")&&(id.equals("movierulz")||id.equals(EinthusanSimulatedProvider.ID)||Library.netmirror(id))||filter.equals("Shows")&&(id.equals("tamil")||id.equals("telugu")||Library.netmirror(id))||filter.equals("Live")&&id.equals("news");}
    private boolean categoryType(Library.Title t){
        if(filter.equals("All"))return true;
        if(filter.equals("Movies"))return t.type.equals("movie")||t.provider.equals("movierulz")||Library.netmirror(t.provider)&&!t.type.equals("tv")&&!t.type.equals("series");
        if(filter.equals("Shows"))return t.type.equals("series")||t.type.equals("episode")||t.provider.equals("tamil")||t.provider.equals("telugu");
        return t.type.equals("tv")||t.provider.equals("news");
    }
    private List<Library.Title> pooled(){
        BrowseFilter f=BrowseFilter.load(uiPrefs());List<Library.Title> items=new ArrayList<>();
        for(Library.Title t:allTitles())if(categoryType(t)&&f.matches(t,position(t)))items.add(t);
        CatalogRanking.Merged merged=CatalogRanking.dedupe(items);alternates=merged.alternates;
        trendDay=CatalogRanking.matchTrends(merged.titles,trendsDay);trendWeek=CatalogRanking.matchTrends(merged.titles,trendsWeek);
        return CatalogSort.by(merged.titles,f.sort,signals());
    }
    private CatalogSort.Signals signals(){
        return new CatalogSort.Signals(){
            @Override public int trendDay(Library.Title t){Integer r=trendDay.get(t.key());return r==null?0:r;}
            @Override public int trendWeek(Library.Title t){Integer r=trendWeek.get(t.key());return r==null?0:r;}
            @Override public long firstSeen(Library.Title t){return catalogIndex.firstSeen(t.key());}
        };
    }
    private void loadTrending(){
        if(ApiKeys.tmdb(this).isEmpty())return;
        repository.io.execute(()->{try{List<CatalogRanking.Trend> day=metadata.trending("day"),week=metadata.trending("week");runOnUiThread(()->{if(isDestroyed())return;trendsDay=day;trendsWeek=week;if(screen.equals("Home"))renderHomeShelves();else if(screen.equals("Trending"))showTrending();});}catch(Exception ignored){/* shelves stay hidden without data */}});
    }
    private List<Library.Title> ranked(List<Library.Title> pool,Map<String,Integer> ranks){
        List<Library.Title> out=new ArrayList<>();for(Library.Title t:pool)if(ranks.containsKey(t.key()))out.add(t);
        out.sort(Comparator.comparingInt(t->ranks.get(t.key())));return out;
    }
    private List<Library.Title> recentlyAdded(List<Library.Title> pool){
        long cutoff=System.currentTimeMillis()-RECENT_MS;List<Library.Title> out=new ArrayList<>();
        for(Library.Title t:pool){long seen=catalogIndex.firstSeen(t.key());if(seen>cutoff)out.add(t);}
        return CatalogSort.by(out,"added",signals());
    }
    private boolean movieLike(Library.Title t){return !t.type.equals("tv")&&!t.type.equals("episode");}
    private void showTrending(){
        shell("Trending",false);heading("Trending","Ranked by TMDB's daily and weekly trending charts, matched to titles your sources actually have.");
        if(ApiKeys.tmdb(this).isEmpty()){body.addView(ui.text("Trending needs a TMDB API key (Settings → Metadata API keys). Without it, Saandio shows no trending ranking rather than guessing one.",15,TvUi.MUTED));return;}
        List<Library.Title> pool=pooled();List<Library.Title> day=ranked(pool,trendDay),week=ranked(pool,trendWeek);
        if(day.isEmpty()&&week.isEmpty()){body.addView(ui.text(trendsDay.isEmpty()?"Loading trending charts…":"None of today's trending films are in your connected sources.",15,TvUi.MUTED));return;}
        if(!day.isEmpty())shelf(body,"Trending Now",day,false);
        if(!week.isEmpty())shelf(body,"Popular This Week",week,false);
    }
    private void showLatest(){
        shell("Latest Releases",false);heading("Latest Releases","Newest release date first. Titles without a known date are listed last.");
        List<Library.Title> movies=new ArrayList<>();for(Library.Title t:pooled())if(movieLike(t))movies.add(t);
        if(movies.isEmpty())body.addView(ui.text("No titles yet. Enable a source in Settings → Add-ons.",15,TvUi.MUTED));else grid(body,CatalogSort.newestFirst(movies));
    }
    private void showLanguages(String selected){
        browseSelection=selected;shell("Languages",false);heading("Languages","Pick a language. Titles with no language from their source are not guessed.");
        List<Library.Title> pool=pooled();Map<String,List<Library.Title>> groups=new LinkedHashMap<>();for(String l:LanguageTaxonomy.ALL)groups.put(l,new ArrayList<>());
        for(Library.Title t:pool){String l=LanguageTaxonomy.of(t);if(!l.isEmpty()&&movieLike(t))groups.get(l).add(t);}
        chipRows(groups,selected,this::showLanguages,l->l);
        if(!selected.isEmpty()){List<Library.Title> list=groups.getOrDefault(selected,new ArrayList<>());if(list.isEmpty())body.addView(ui.text("No "+selected+" titles in your sources yet.",15,TvUi.MUTED));else grid(body,CatalogSort.newestFirst(list));}
    }
    private void showGenres(String selected){
        browseSelection=selected;shell("Genres",false);heading("Genres","Genres from your sources, normalised to one list.");
        List<Library.Title> pool=pooled();Map<String,List<Library.Title>> groups=new LinkedHashMap<>();for(String g:GenreTaxonomy.BROWSE)groups.put(g,new ArrayList<>());
        for(Library.Title t:pool)for(String g:GenreTaxonomy.of(t))if(groups.containsKey(g))groups.get(g).add(t);
        chipRows(groups,selected,this::showGenres,GenreTaxonomy::display);
        if(!selected.isEmpty()){List<Library.Title> list=groups.getOrDefault(selected,new ArrayList<>());if(list.isEmpty())body.addView(ui.text("No "+GenreTaxonomy.display(selected)+" titles in your sources yet.",15,TvUi.MUTED));else grid(body,CatalogSort.newestFirst(list));}
    }
    private void chipRows(Map<String,List<Library.Title>> groups,String selected,java.util.function.Consumer<String> open,java.util.function.Function<String,String> label){
        LinearLayout row=null;int i=0;LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(-2,-2);m.setMargins(0,0,ui.dp(10),0);
        for(Map.Entry<String,List<Library.Title>> e:groups.entrySet()){
            if(e.getValue().isEmpty()&&!e.getKey().equals(selected))continue;
            if(i%7==0){row=new LinearLayout(this);row.setClipChildren(false);body.addView(row);ui.gap(body,10);}
            String key=e.getKey();row.addView(ui.action(label.apply(key)+" · "+e.getValue().size(),key.equals(selected),()->open.accept(key)),m);i++;
        }
        if(i==0)body.addView(ui.text("Nothing to browse yet. Catalogs may still be loading.",15,TvUi.MUTED));
        ui.gap(body,16);
    }
    private void renderHomeShelves(){
        if(homeShelves==null)return;homeShelves.removeAllViews();
        boolean pending=false;for(Library.Addon a:Library.addons())if(repository.enabled(a.id)&&categoryMatches(a.id)&&loading.contains(a.id)&&!catalogs.containsKey(a.id))pending=true;
        if(pending){homeShelves.addView(new ShimmerView(this),new LinearLayout.LayoutParams(-1,ui.dp(128)));ui.gap(homeShelves,22);}
        List<Library.Title> pool=pooled();
        if(pool.isEmpty()&&!pending){homeShelves.addView(ui.text("Nothing matches these filters yet. Catalogs still loading stay as skeletons above.",15,TvUi.MUTED));}
        List<Library.Title> movies=new ArrayList<>();for(Library.Title t:pool)if(movieLike(t))movies.add(t);
        List<Library.Title> trendingNow=ranked(movies,trendDay);if(trendingNow.size()>=3)shelf(homeShelves,"Trending Now",trendingNow,false);
        if(!movies.isEmpty())shelf(homeShelves,"Latest Releases",CatalogSort.newestFirst(movies),false);
        List<Library.Title> added=recentlyAdded(pool);if(!added.isEmpty())shelf(homeShelves,"Recently Added",added,false);
        List<Library.Title> weekly=ranked(movies,trendWeek);if(weekly.size()>=3)shelf(homeShelves,"Popular This Week",weekly,false);
        for(String language:new String[]{"Telugu","Tamil","Hindi","Malayalam","Kannada","Bengali","Marathi","Punjabi","English"}){
            List<Library.Title> group=new ArrayList<>();for(Library.Title t:movies)if(!t.type.equals("series")&&language.equals(LanguageTaxonomy.of(t)))group.add(t);
            if(group.size()>=3)shelf(homeShelves,language.equals("English")?"Hollywood Movies":language+" Movies",CatalogSort.newestFirst(group),false);
        }
        List<Library.Title> rated=new ArrayList<>();for(Library.Title t:movies)if(CatalogSort.popularity(t)>0)rated.add(t);
        if(rated.size()>=3)shelf(homeShelves,"Top Rated",CatalogSort.by(rated,"rating"),false);
        LinearLayout genreRow=new LinearLayout(this);genreRow.setClipChildren(false);LinearLayout.LayoutParams gm=new LinearLayout.LayoutParams(-2,-2);gm.setMargins(0,0,ui.dp(10),0);
        for(String g:GenreTaxonomy.BROWSE){int n=0;for(Library.Title t:movies)if(GenreTaxonomy.of(t).contains(g))n++;if(n>0)genreRow.addView(ui.action(GenreTaxonomy.display(g),false,()->{history.push(capture());showGenres(g);}),gm);}
        if(genreRow.getChildCount()>0){homeShelves.addView(ui.bold("Browse by Genre",19));ui.gap(homeShelves,10);HorizontalScrollView gs=new HorizontalScrollView(this);gs.setHorizontalScrollBarEnabled(false);gs.setClipChildren(false);gs.addView(genreRow);homeShelves.addView(gs);ui.gap(homeShelves,24);}
        List<Library.Title> continuing=new ArrayList<>();for(Library.Title t:pool)if(position(t)>0)continuing.add(t);if(!continuing.isEmpty())shelf(homeShelves,"Continue Watching",continuing,false);
        List<Library.Title> indian=new ArrayList<>();for(Library.Title t:pool)if(isIndianLanguage(t.language)&&!t.type.equals("tv"))indian.add(t);
        if(indian.size()>=3)shelf(homeShelves,"Indian Cinema",indian,false);
        for(String genre:new String[]{"Action","Comedy","Thriller","Romance","Horror","Family"}){
            List<Library.Title> group=new ArrayList<>();for(Library.Title t:pool)if(GenreTaxonomy.of(t).contains(genre))group.add(t);
            if(group.size()>=3)shelf(homeShelves,genre,group,false);
        }
        List<Library.Title> recommended=recommendations(pool);if(recommended.size()>=3)shelf(homeShelves,"Recommended for You",recommended,false);
        List<Library.Title> library=new ArrayList<>();for(Library.Title t:Library.titles(this))if(categoryType(t))library.add(t);if(!library.isEmpty())shelf(homeShelves,"Your library",CatalogSort.newestFirst(library),false);
        if(filter.equals("All")||filter.equals("Movies"))shelf(homeShelves,"Open cinema",Library.samples(),true);
        if(!heroChosen&&!pool.isEmpty()){heroChosen=true;setHero(pool.get(0));}
        else if(!heroChosen){for(Library.Title t:Library.samples()){heroChosen=true;setHero(t);break;}}
    }
    private boolean isIndianLanguage(String language){
        for(String name:new String[]{"Telugu","Tamil","Hindi","Malayalam","Kannada","Bengali","Punjabi","Marathi","Gujarati","Urdu","Odia","Assamese","Bhojpuri","Sanskrit"})if(name.equalsIgnoreCase(language))return true;return false;
    }
    private List<Library.Title> recommendations(List<Library.Title> pool){
        String preferred="";int best=0;Map<String,Integer> counts=new HashMap<>();
        for(Library.Title t:pool)if(position(t)>0&&!t.language.isEmpty())counts.put(t.language,counts.getOrDefault(t.language,0)+1);
        for(Map.Entry<String,Integer> e:counts.entrySet())if(e.getValue()>best){best=e.getValue();preferred=e.getKey();}
        List<Library.Title> out=new ArrayList<>();if(preferred.isEmpty())return out;
        for(Library.Title t:pool)if(preferred.equalsIgnoreCase(t.language)&&position(t)==0&&!t.type.equals("tv"))out.add(t);
        return out.size()>12?out.subList(0,12):out;
    }
    private void setHero(Library.Title title){
        if(heroTitle==null)return;featured=title;heroTitle.setText(title.name);heroProvider.setText(title.type.equals("tv")?"LIVE":title.language.isEmpty()?"MOVIE":title.language.toUpperCase(Locale.ROOT));
        heroMeta.setText(join(title.year,title.language,title.type.equals("tv")?"Live":""));
        if(heroChips!=null){heroChips.removeAllViews();
            if(!title.rating.isEmpty()){heroChips.addView(ui.badge("★ "+title.rating,0xccf5c451));}
            for(String g:GenreTaxonomy.of(title)){
                if(g.equals(GenreTaxonomy.OTHER)&&heroChips.getChildCount()>0)continue;
                LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=ui.dp(8);
                heroChips.addView(ui.chip(g),heroChips.getChildCount()==0?new LinearLayout.LayoutParams(-2,-2):gap);
                if(heroChips.getChildCount()>=5)break;
            }
        }
        if(heroWatch!=null)heroWatch.setText(prefs.getBoolean("saved."+title.key(),false)?"✓  In watchlist":"+  Watchlist");
        heroDescription.setText(title.description.isEmpty()?"Discover details and available sources for this title.":title.description);
        String art=title.background.isEmpty()?title.poster:title.background;
        int known=ColorExtractor.cached(art);if(known!=0)tintHero(known);
        Artwork.load(backdrop,art,bitmap->{if(featured!=title||isDestroyed())return;tintHero(ColorExtractor.extract(art,bitmap));if(topNavigation!=null)topNavigation.artwork(backdrop.getDrawable());});
        if(trailers!=null)trailers.consider(title);
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
    private void updatePanel(Library.Addon addon){if(screen.equals("Home"))renderHomeShelves();}
    private void shelf(LinearLayout parent,String name,List<Library.Title> titles,boolean wide){
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(ui.bold(name,20),new LinearLayout.LayoutParams(0,-2,1));
        parent.addView(heading);ui.gap(parent,8);
        boolean landscape=wide||compact();RecyclerView row=new RecyclerView(this);row.setHorizontalScrollBarEnabled(false);row.setClipChildren(false);row.setClipToPadding(false);row.setPadding(ui.dp(4),ui.dp(6),ui.dp(8),ui.dp(8));
        LinearLayoutManager layout=new LinearLayoutManager(this,RecyclerView.HORIZONTAL,false);row.setLayoutManager(layout);row.setItemAnimator(null);row.setNestedScrollingEnabled(false);
        row.setAdapter(new ShelfAdapter(titles,landscape,name));parent.addView(row,new LinearLayout.LayoutParams(-1,ui.dp(landscape?184:244)));ui.gap(parent,20);
        String remember=rowFocus.containsKey(name)?rowFocus.get(name):focusKey;
        for(int i=0;i<titles.size();i++)if(titles.get(i).key().equals(remember)){final String key=remember;layout.scrollToPosition(i);row.post(()->{View found=findTagged(row,key);if(found!=null)found.requestFocus();});break;}
    }
    private final class ShelfAdapter extends RecyclerView.Adapter<ShelfHolder>{
        final List<Library.Title> titles;final boolean wide;final String shelf;
        ShelfAdapter(List<Library.Title> titles,boolean wide,String shelf){this.titles=titles;this.wide=wide;this.shelf=shelf;setHasStableIds(true);}
        @Override public long getItemId(int index){return titles.get(index).key().hashCode();}
        @Override public ShelfHolder onCreateViewHolder(android.view.ViewGroup parent,int type){FrameLayout slot=new FrameLayout(MainActivity.this);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(ui.dp(wide?224:139),-1);p.rightMargin=ui.dp(13);slot.setLayoutParams(p);slot.setClipChildren(false);return new ShelfHolder(slot);}
        @Override public void onBindViewHolder(ShelfHolder holder,int index){
            FrameLayout slot=(FrameLayout)holder.itemView;slot.removeAllViews();Library.Title t=titles.get(index);slot.addView(card(t,wide,shelf),new FrameLayout.LayoutParams(-1,-2));
            Artwork.prefetch(slot.getContext(),t.poster);if(index+1<titles.size()){Artwork.prefetch(slot.getContext(),titles.get(index+1).poster);if(trailers!=null)trailers.preload(titles.get(index+1));}
        }
        @Override public int getItemCount(){return titles.size();}
    }
    private static final class ShelfHolder extends RecyclerView.ViewHolder{ShelfHolder(View view){super(view);}}
    private LinearLayout.LayoutParams cardParams(boolean wide){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(wide?224:139),-2);p.setMargins(0,0,ui.dp(13),0);return p;}
    private View card(Library.Title t,boolean wide){return card(t,wide,"");}
    private View card(Library.Title t,boolean wide,String shelf){
        LinearLayout card=ui.column();card.setPadding(ui.dp(4),ui.dp(4),ui.dp(4),ui.dp(6));card.setTag(t.key());card.setContentDescription(t.name+", "+t.providerName);
        FrameLayout artwork=new FrameLayout(this);int tint=0xff1b2638|(t.name.hashCode()&0x00181818);artwork.setBackground(ui.shape(tint,0,18));artwork.setClipToOutline(true);
        TextView fallback=ui.bold(t.name.substring(0,Math.min(2,t.name.length())).toUpperCase(Locale.ROOT),30);fallback.setGravity(Gravity.CENTER);artwork.addView(fallback,new FrameLayout.LayoutParams(-1,-1));
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.addView(image,new FrameLayout.LayoutParams(-1,-1));Artwork.load(image,t.poster);
        if(position(t)>0){ProgressBar progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setProgress(20);FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,ui.dp(3),Gravity.BOTTOM);artwork.addView(progress,pp);}
        card.addView(artwork,new LinearLayout.LayoutParams(-1,ui.dp(wide?124:184)));ui.gap(card,8);TextView title=ui.bold(t.name,13);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);card.addView(title);ui.gap(card,4);TextView subtitle=ui.text(join(t.year,t.language,t.type.equals("tv")?"LIVE":t.providerName),10,TvUi.MUTED);subtitle.setMaxLines(1);subtitle.setEllipsize(TextUtils.TruncateAt.END);card.addView(subtitle);
        ui.focusCard(card,()->{focusKey=t.key();if(!shelf.isEmpty())rowFocus.put(shelf,t.key());if(screen.equals("Home"))setHero(t);else if(trailers!=null)trailers.consider(t);
            if(Library.netmirror(t.provider)&&t.name.startsWith("NetMirror · ")&&enriching.add(t.key()))repository.io.execute(()->{try{Library.Title full=repository.details(t);runOnUiThread(()->{enriching.remove(t.key());if(isDestroyed())return;title.setText(full.name);persist(full);if(card.hasFocus()&&screen.equals("Home"))setHero(full);});}catch(Exception ignored){runOnUiThread(()->enriching.remove(t.key()));}});
        });card.setOnClickListener(v->{if(screen.equals("Home"))homeY=scroll.getScrollY();openDetails(t);});
        card.setOnLongClickListener(v->{toggleWatchlist(t);return true;});return card;
    }
    private void refreshAll(){List<Library.Addon> ordered=new ArrayList<>(Library.addons());ordered.sort(Comparator.comparing(a->Library.netmirror(a.id)?1:0));for(Library.Addon addon:ordered)if(repository.enabled(addon.id))load(addon,1);}
    private void load(Library.Addon addon,int page){
        if(!loading.add(addon.id))return;states.put(addon.id,"Loading catalog…");
        if(screen.equals("Home"))updatePanel(addon);
        String signature=configSignature(addon.id);
        repository.io.execute(()->{
            ProviderRepository.Catalog result=null;String failure=null;try{result=repository.load(addon,"",page);if(page==1&&!result.stale)catalogIndex.observe(addon.id,result.titles);}catch(Exception e){failure=ProviderRepository.message(e);}
            ProviderRepository.Catalog value=result;String error=failure;
            runOnUiThread(()->{if(isDestroyed())return;loading.remove(addon.id);
                if(!signature.equals(configSignature(addon.id))){load(addon,1);return;}
                research.record(addon.id,"catalog",value!=null&&!value.stale);
                if(value!=null){if(page==1)catalogs.put(addon.id,value);browsePages.put(addon.id,value);states.put(addon.id,value.status);}else states.put(addon.id,error);
                if(screen.equals("Home"))updatePanel(addon);else if(screen.equals("Provider:"+addon.id))showProvider(addon,value==null?Math.max(1,page-1):page);else if(screen.equals("Add-ons")){TextView status=addonStatus.get(addon.id),count=addonCount.get(addon.id);if(status!=null)status.setText(states.get(addon.id));if(count!=null)count.setText(value==null?"Needs attention":value.total+" listed items");}
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
    private void grid(LinearLayout parent,List<Library.Title> titles){
        grid(parent,titles,false);
    }
    private void grid(LinearLayout parent,List<Library.Title> titles,boolean wide){
        LinearLayout row=null;int width=getResources().getDisplayMetrics().widthPixels;int cols=Math.max(2,(int)(width/getResources().getDisplayMetrics().density-160)/(wide?237:153));
        for(int i=0;i<titles.size();i++){if(i%cols==0){row=new LinearLayout(this);row.setClipChildren(false);parent.addView(row);ui.gap(parent,14);}row.addView(card(titles.get(i),wide),cardParams(wide));}
    }
    private void showDetails(Library.Title title){
        detailTitle=title;shell("Details",true);Artwork.load(backdrop,title.background.isEmpty()?title.poster:title.background);
        TextView back=ui.action("‹  Back",false,this::back);body.addView(back,new LinearLayout.LayoutParams(-2,ui.dp(38)));ui.gap(body,26);
        String kicker=join(LanguageTaxonomy.of(title),title.type.equals("series")?"Series":title.type.equals("tv")?"Live":"Movie").toUpperCase(Locale.ROOT);
        TextView provider=ui.text(kicker,11,TvUi.ACCENT);provider.setLetterSpacing(.18f);body.addView(provider);ui.gap(body,12);TextView name=ui.bold(title.name,34);body.addView(name);ui.gap(body,12);TextView metaLine=ui.text(join(title.year,title.language,title.genre,title.rating.isEmpty()?"":"★ "+title.rating),14,TvUi.WHITE);body.addView(metaLine);ui.gap(body,16);
        TextView description=ui.text(title.description.isEmpty()?"Loading title details…":title.description,15,TvUi.MUTED);description.setMaxWidth(ui.dp(650));body.addView(description);ui.gap(body,22);
        LinearLayout actions=new LinearLayout(this);actions.setClipChildren(false);
        TextView play=ui.action(title.type.equals("series")?"Browse episodes":position(title)>0?"▶  Resume":"▶  Play",true,()->findStreams(detailTitle));actions.addView(play);LinearLayout.LayoutParams next=new LinearLayout.LayoutParams(-2,-2);next.setMargins(ui.dp(12),0,0,0);
        TextView saved=ui.action(prefs.getBoolean("saved."+title.key(),false)?"✓  In watchlist":"+  Watchlist",false,()->{boolean value=!prefs.getBoolean("saved."+title.key(),false);prefs.edit().putBoolean("saved."+title.key(),value).apply();persist(title);showDetails(detailTitle);});actions.addView(saved,next);
        TextView episodes=ui.action("Episodes",false,()->showEpisodes(detailTitle,-1));if(title.type.equals("series"))actions.addView(episodes,next);
        List<Library.Title> others=alternates.getOrDefault(title.key(),Collections.emptyList());
        if(!others.isEmpty())actions.addView(ui.action("Other sources · "+others.size(),false,()->{String[] names=new String[others.size()];for(int i=0;i<names.length;i++)names[i]=others.get(i).providerName;new AlertDialog.Builder(this).setTitle("Also available from").setItems(names,(d,w)->findStreams(others.get(w))).setNegativeButton("Back",null).show();}),next);
        body.addView(actions);ui.gap(body,12);
        body.addView(ui.text("Source: "+title.providerName+(others.isEmpty()?"":" · also listed by "+others.size()+" other source"+(others.size()==1?"":"s")),12,TvUi.MUTED));ui.gap(body,22);
        LinearLayout ratings=new LinearLayout(this);body.addView(ratings);ui.gap(body,16);
        TextView tagline=ui.text("",15,TvUi.ACCENT);tagline.setVisibility(View.GONE);body.addView(tagline);ui.gap(body,10);
        body.addView(ui.bold("Cast & creators",19));ui.gap(body,8);TextView cast=ui.text(title.cast.isEmpty()?"Cast information has not been supplied by this source.":title.cast,14,TvUi.MUTED);body.addView(cast);ui.gap(body,8);
        LinearLayout people=new LinearLayout(this);people.setVisibility(View.GONE);body.addView(people);ui.gap(body,18);
        LinearLayout extra=ui.column();body.addView(extra);
        body.addView(ui.bold("Your review",19));ui.gap(body,8);TextView note=ui.text(prefs.getString("review."+title.key(),"You haven't reviewed this title yet."),14,TvUi.MUTED);body.addView(note);ui.gap(body,12);body.addView(ui.action("Write a review",false,()->review(title)));
        ui.gap(body,16);body.addView(ui.action("Correct metadata match",false,()->correctMatch(title)));
        play.requestFocus();int version=generation;
        repository.io.execute(()->{try{Library.Title full=repository.details(title);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;detailTitle=full;name.setText(full.name);metaLine.setText(join(full.year,full.language,full.genre,full.rating.isEmpty()?"":"★ "+full.rating));description.setText(full.description.isEmpty()?"No synopsis has been supplied by this source.":full.description);cast.setText(full.cast.isEmpty()?"Cast information has not been supplied by this source.":full.cast);persist(full);if(full.type.equals("series")&&episodes.getParent()==null)actions.addView(episodes,next);});}catch(Exception e){runOnUiThread(()->{if(generation==version)description.setText(title.description.isEmpty()?"Details unavailable. "+ProviderRepository.message(e):title.description);});}});
        repository.io.execute(()->{try{org.json.JSONObject rec=ApiKeys.metadataReady(this)?metadata.enrich(title):null;runOnUiThread(()->{if(isDestroyed()||generation!=version||detailTitle==null||!detailTitle.key().equals(title.key()))return;bindMetadata(title,rec,ratings,tagline,description,cast,people,extra,metaLine);});}catch(Exception ignored){runOnUiThread(()->{if(generation==version)extra.addView(ui.text(ApiKeys.metadataReady(this)?"Additional metadata could not be loaded.":"Metadata unavailable. Add a TMDB API key in Settings.",13,TvUi.MUTED));});}});
    }
    private void findStreams(Library.Title title){
        if(title.type.equals("series")){showEpisodes(title,-1);return;}
        TextView status=ui.text("Finding playable sources…",17,TvUi.WHITE);status.setPadding(ui.dp(24),ui.dp(24),ui.dp(24),ui.dp(24));AlertDialog progress=new AlertDialog.Builder(this).setTitle(title.name).setView(status).setNegativeButton("Cancel",null).create();progress.show();
        repository.io.execute(()->{try{List<ProviderRepository.Stream> streams=repository.streams(title);runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();String[] names=new String[streams.size()];for(int i=0;i<names.length;i++)names[i]=PlaybackPolicy.sourceChoice(streams.get(i).name);new AlertDialog.Builder(this).setTitle("Choose a source").setItems(names,(d,w)->play(title,streams.get(w))).setNegativeButton("Back",null).show();});}catch(Exception e){runOnUiThread(()->{if(isDestroyed()||!progress.isShowing())return;progress.dismiss();new AlertDialog.Builder(this).setTitle("Source unavailable").setMessage(ProviderRepository.message(e)).setPositiveButton("Back to title",null).show();});}});
    }
    private void play(Library.Title title,ProviderRepository.Stream stream){
        VpnController vpn=VpnController.get(this);VpnHealth.State own=vpn.snapshot().state;
        boolean otherVpn=(own==VpnHealth.State.OFF||own==VpnHealth.State.NOT_CONFIGURED)&&NetworkStatus.vpnActive(this);
        if(!PlaybackPolicy.allow(uiPrefs().getBoolean("vpn.required",false),vpn.claimsProtection()||otherVpn)){notice("VPN required","Playback is set to require a protected VPN connection. Current state: "+VpnHealth.label(own)+". This title will not play on the normal connection.");return;}
        TextView status=ui.text("Checking this source…",16,TvUi.WHITE);status.setPadding(ui.dp(24),ui.dp(20),ui.dp(24),ui.dp(20));AlertDialog check=new AlertDialog.Builder(this).setTitle("Preparing playback").setView(status).setNegativeButton("Cancel",null).create();check.show();
        repository.io.execute(()->{try{StreamProbe.inspect(stream);research.record(title.provider,"source_check",true);runOnUiThread(()->{if(isDestroyed()||!check.isShowing())return;check.dismiss();persist(title);Intent intent=new Intent(this,PlayerActivity.class).putExtra("id",title.key()).putExtra("title",title.name).putExtra("url",stream.url).putExtra("mimeType",stream.mimeType).putExtra("headers",new JSONObject(stream.headers).toString()).putExtra("subtitles",stream.subtitles.toString());startActivity(intent);});}catch(Exception e){research.record(title.provider,"source_check",false);runOnUiThread(()->{if(!isDestroyed()&&check.isShowing()){check.dismiss();notice("Source unavailable",ProviderRepository.message(e));}});}});
    }
    private void showEpisodes(Library.Title title,int season){
        openEpisodes(title,season,1,true);
    }
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
    private void showWatchlist(){shell("Watchlist",false);heading("Your watchlist","Everything you saved, in one place.");List<Library.Title> saved=new ArrayList<>();for(Library.Title t:pooled())if(prefs.getBoolean("saved."+t.key(),false))saved.add(t);if(saved.isEmpty())body.addView(ui.text("Open a title and choose + Watchlist to save it here.",16,TvUi.MUTED));else grid(body,CatalogSort.newestFirst(saved));}
    private void showSearch(){
        shell("Search",false);heading("Find your next watch","On-screen keyboard, voice, and grouped results.");
        EditText input=ui.input("Title, cast or genre",searchQuery);body.addView(input);ui.gap(body,12);
        LinearLayout tools=new LinearLayout(this);TextView status=ui.text("",12,TvUi.MUTED);LinearLayout results=ui.column();
        Runnable remember=()->{String q=input.getText().toString().trim();if(q.isEmpty())return;try{JSONArray old=new JSONArray(uiPrefs().getString("search.recent","[]")),next=new JSONArray();next.put(q);for(int i=0;i<old.length()&&next.length()<12;i++)if(!q.equalsIgnoreCase(old.optString(i)))next.put(old.get(i));uiPrefs().edit().putString("search.recent",next.toString()).apply();}catch(JSONException ignored){}};
        Runnable local=()->{results.removeAllViews();String q=input.getText().toString().toLowerCase(Locale.ROOT).trim();
            if(q.isEmpty()){try{JSONArray recent=new JSONArray(uiPrefs().getString("search.recent","[]"));if(recent.length()>0){results.addView(ui.bold("Recent searches",16));ui.gap(results,10);LinearLayout row=new LinearLayout(this);for(int i=0;i<recent.length();i++){String item=recent.optString(i);row.addView(ui.action(item,false,()->{input.setText(item);searchQuery=item;}));}results.addView(row);ui.gap(results,18);}}catch(JSONException ignored){}status.setText("Type, speak, or search connected sources");return;}
            BrowseFilter f=BrowseFilter.load(uiPrefs());Map<String,List<Library.Title>> grouped=new LinkedHashMap<>();
            for(Library.Title t:allTitles())if((t.name+" "+t.cast+" "+t.genre+" "+t.language).toLowerCase(Locale.ROOT).contains(q)&&f.matches(t,position(t)))grouped.computeIfAbsent(t.type.equals("tv")?"Live":t.type.equals("series")||t.type.equals("episode")?"Shows":t.language.isEmpty()?"Movies":t.language+" Movies",k->new ArrayList<>()).add(t);
            int count=0;for(Map.Entry<String,List<Library.Title>> e:grouped.entrySet()){shelf(results,e.getKey(),CatalogSort.newestFirst(e.getValue()),true);count+=e.getValue().size();}
            status.setText(count+" matching titles in loaded catalogs");};
        Runnable remote=()->{String query=input.getText().toString().trim();if(query.isEmpty())return;remember.run();status.setText("Searching sources…");int version=generation;results.removeAllViews();for(Library.Addon a:Library.addons())if(repository.enabled(a.id))repository.io.execute(()->{try{ProviderRepository.Catalog found=repository.load(a,query,1);runOnUiThread(()->{if(generation!=version)return;if(!found.titles.isEmpty())shelf(results,"Results",CatalogSort.newestFirst(found.titles),true);status.setText("Search results");});}catch(Exception ignored){runOnUiThread(()->{if(generation==version)status.setText("Search complete for available sources");});}});};
        tools.addView(ui.action("Search connected sources",true,remote));LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=ui.dp(10);
        tools.addView(ui.action("Voice search",false,()->{try{startActivityForResult(new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT,"Search Saandio TV"),40);}catch(Exception e){notice("Voice search unavailable","This device has no speech recognizer.");}}),gap);
        tools.addView(ui.action("Filters",false,this::showFilters),gap);body.addView(tools);ui.gap(body,14);body.addView(status);ui.gap(body,16);body.addView(results);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setOnEditorActionListener((v,id,event)->{remote.run();return true;});input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){searchQuery=s.toString();local.run();}public void afterTextChanged(Editable e){}});local.run();
    }
    private void showAddons(){
        shell("Add-ons",false);heading("Your sources","Native catalogs. Native playback. No website launchers.");
        for(Library.Addon a:Library.addons()){
            LinearLayout panel=ui.column();panel.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(16));panel.setBackground(ui.shape(0xff151b26,0,12));
            LinearLayout line=new LinearLayout(this);line.addView(ui.bold(a.name,21),new LinearLayout.LayoutParams(0,-2,1));ProviderRepository.Catalog cat=catalogs.get(a.id);
            String state=!repository.enabled(a.id)?"Disabled":cat!=null?cat.total+" listed items":"Needs attention";TextView count=ui.text(state,12,cat!=null?0xff94c8a3:TvUi.MUTED);addonCount.put(a.id,count);line.addView(count);panel.addView(line);ui.gap(panel,8);TextView info=ui.text(states.getOrDefault(a.id,a.description),12,TvUi.MUTED);info.setMaxLines(2);addonStatus.put(a.id,info);panel.addView(info);ui.gap(panel,14);
            LinearLayout actions=new LinearLayout(this);actions.addView(ui.action("Browse",true,()->openProvider(a)));LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-2,-2);margin.setMargins(ui.dp(10),0,0,0);actions.addView(ui.action("Configure",false,()->configure(a)),margin);actions.addView(ui.action(repository.enabled(a.id)?"Disable":"Enable",false,()->{prefs.edit().putBoolean("addon."+a.id,!repository.enabled(a.id)).apply();showAddons();if(repository.enabled(a.id))load(a,1);}),margin);panel.addView(actions);body.addView(panel);ui.gap(body,14);
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
        shell("Settings",false);heading("Make yourself at home","Saandio TV 0.3 · research edition");
        android.content.SharedPreferences appearance=getSharedPreferences("astra_ui_v1",0);body.addView(ui.action("Reduce motion / low-end device · "+(appearance.getBoolean("reduce_motion",false)?"On":"Off"),false,()->{appearance.edit().putBoolean("reduce_motion",!appearance.getBoolean("reduce_motion",false)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Autoplay trailers · "+(appearance.getBoolean("trailer.autoplay",true)?"On":"Off"),false,()->{appearance.edit().putBoolean("trailer.autoplay",!appearance.getBoolean("trailer.autoplay",true)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Trailer sound · "+(appearance.getBoolean("trailer.sound",false)?"On":"Off"),false,()->{appearance.edit().putBoolean("trailer.sound",!appearance.getBoolean("trailer.sound",false)).apply();showSettings();}));ui.gap(body,14);
        int delay=appearance.getInt("trailer.delay",1200);body.addView(ui.action("Trailer delay · "+delay+" ms",false,()->{int next=delay==800?1200:delay==1200?2000:800;appearance.edit().putInt("trailer.delay",next).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Add-ons & sources",true,()->{history.push(capture());showAddons();}));ui.gap(body,14);
        body.addView(ui.action("Import your media catalog",false,()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),20)));ui.gap(body,14);
        body.addView(ui.action("Background audio · "+(prefs.getBoolean("background",false)?"On":"Off"),false,()->{prefs.edit().putBoolean("background",!prefs.getBoolean("background",false)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Refresh connected sources",false,()->{refreshAll();Toast.makeText(this,"Refreshing sources",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Stop playback",false,()->{stopService(new Intent(this,PlaybackService.class));Toast.makeText(this,"Playback stopped",Toast.LENGTH_SHORT).show();}));ui.gap(body,14);
        body.addView(ui.action("Clear watch history",false,()->{android.content.SharedPreferences.Editor edit=prefs.edit();for(String key:prefs.getAll().keySet())if(key.startsWith("position."))edit.remove(key);edit.apply();Toast.makeText(this,"History cleared",Toast.LENGTH_SHORT).show();}));ui.gap(body,24);
        VpnHealth.State vpnState=VpnController.get(this).snapshot().state;
        body.addView(ui.action("VPN · "+(VpnHealth.claimsProtection(vpnState)?"Protected":vpnState==VpnHealth.State.NOT_CONFIGURED?(NetworkStatus.vpnActive(this)?"another VPN app is active":"not set up"):vpnState==VpnHealth.State.OFF?"Disconnected":"Not protected"),false,this::showConnection));ui.gap(body,14);
        body.addView(ui.action("Require VPN before playback · "+(appearance.getBoolean("vpn.required",false)?"On":"Off"),false,()->{appearance.edit().putBoolean("vpn.required",!appearance.getBoolean("vpn.required",false)).apply();showSettings();}));ui.gap(body,14);
        body.addView(ui.action("Metadata API keys · "+(ApiKeys.metadataReady(this)?"configured":"not configured"),false,this::editApiKeys));ui.gap(body,14);
        body.addView(ui.text("Einthusan: "+EinthusanAdapter.status().reason,13,TvUi.MUTED));ui.gap(body,14);
        body.addView(ui.action("Source health & research",false,this::showResearch));ui.gap(body,16);
        if(TesterGate.configured(BuildConfig.TESTER_PIN_HASH)){body.addView(ui.action("Security research (authorized testers)",false,()->startActivity(new Intent(this,SecurityDashboardActivity.class))));ui.gap(body,16);}
        body.addView(ui.text("Playback supports MP4, HLS and DASH, embedded audio tracks, and supplied subtitles. A source must expose playable media; website-only and protected sources can be unavailable.",14,TvUi.MUTED));
        ui.gap(body,12);body.addView(ui.text("Metadata keys are stored on-device in app-private settings, or supplied at build time via local.properties. They are never written to the research log.",13,TvUi.MUTED));
    }
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
        box.addView(ui.text("TMDB is required for matches, cast and similar titles. OMDb adds IMDb / Rotten Tomatoes / Metacritic. Trakt is optional. YouTube Data is used only for trailer search in Phase 4.",13,TvUi.MUTED));ui.gap(box,12);
        EditText tmdb=ui.input("TMDB API key",ApiKeys.tmdb(this));box.addView(tmdb);ui.gap(box,8);
        EditText omdb=ui.input("OMDb API key",ApiKeys.omdb(this));box.addView(omdb);ui.gap(box,8);
        EditText trakt=ui.input("Trakt client ID",ApiKeys.trakt(this));box.addView(trakt);ui.gap(box,8);
        EditText youtube=ui.input("YouTube Data API key",ApiKeys.youtube(this));box.addView(youtube);
        new AlertDialog.Builder(this).setTitle("Metadata keys").setView(box).setPositiveButton("Save",(d,w)->{ApiKeys.save(this,tmdb.getText().toString(),omdb.getText().toString(),trakt.getText().toString(),youtube.getText().toString());showSettings();}).setNegativeButton("Cancel",null).show();
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
    private void bindMetadata(Library.Title title,org.json.JSONObject rec,LinearLayout ratings,TextView tagline,TextView description,TextView cast,LinearLayout people,LinearLayout extra,TextView metaLine){
        extra.removeAllViews();ratings.removeAllViews();
        if(rec==null||!ApiKeys.metadataReady(this)){extra.addView(ui.text("Metadata unavailable. Add a TMDB API key in Settings.",13,TvUi.MUTED));return;}
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
        if(similar!=null&&similar.length()>0){List<Library.Title> related=new ArrayList<>();for(int i=0;i<similar.length();i++){JSONObject s=similar.optJSONObject(i);if(s==null)continue;Library.Title local=findCatalog(s.optString("name"));if(local!=null)related.add(local);}if(!related.isEmpty())shelf(extra,"Similar titles",related,false);}
    }
    private void badge(LinearLayout row,String source,String value){
        if(value==null||value.isEmpty())return;
        LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.rightMargin=ui.dp(8);
        LinearLayout chip=ui.column();chip.setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));chip.setBackground(ui.shape(0x5524252b,0,10));
        chip.addView(ui.text(source,9,TvUi.MUTED));chip.addView(ui.bold(value,14));row.addView(chip,gap);
    }
    private String yearOf(String value){java.util.regex.Matcher m=java.util.regex.Pattern.compile("((?:19|20)\\d{2})").matcher(value==null?"":value);return m.find()?m.group(1):value;}
    private Library.Title findCatalog(String name){String n=MetadataMatcher.normalize(name);if(n.isEmpty())return null;for(Library.Title t:allTitles())if(MetadataMatcher.normalize(t.name).equals(n))return t;return null;}
    private void openPerson(String id,String name){Nav n=capture();n.personId=id;n.personName=name;history.push(n);showPerson(id,name,false);}
    private void showPerson(String id,String name,boolean remember){
        shell("Person",true);heading(name,"Filmography from TMDB. Playable titles open only when a connected source lists them.");
        body.addView(ui.action("‹ Back",false,this::back));ui.gap(body,16);LinearLayout list=ui.column();body.addView(list);list.addView(new ShimmerView(this),new LinearLayout.LayoutParams(-1,ui.dp(100)));
        int version=generation;repository.io.execute(()->{try{JSONObject person=metadata.person(id);JSONArray credits=metadata.filmography(id);runOnUiThread(()->{if(isDestroyed()||generation!=version)return;list.removeAllViews();Artwork.load(backdrop,person.optString("profile_path").isEmpty()?"":"https://image.tmdb.org/t/p/w780"+person.optString("profile_path"));
            if(!person.optString("biography").isEmpty()){TextView bio=ui.text(person.optString("biography"),14,TvUi.MUTED);bio.setMaxLines(6);list.addView(bio);ui.gap(list,16);}
            List<Library.Title> playable=new ArrayList<>();for(int i=0;i<Math.min(credits.length(),40);i++){JSONObject c=credits.optJSONObject(i);if(c==null)continue;Library.Title local=findCatalog(c.optString("title",c.optString("name")));if(local!=null)playable.add(local);}
            if(playable.isEmpty())list.addView(ui.text("None of this person's titles are in your connected catalogs.",14,TvUi.MUTED));else shelf(list,"In your catalogs",playable,false);
        });}catch(Exception e){runOnUiThread(()->{if(generation==version)notice("Filmography unavailable","TMDB did not return this person.");});}});
    }
    private void correctMatch(Library.Title title){
        if(!ApiKeys.metadataReady(this)){notice("Metadata unavailable","Add a TMDB API key in Settings.");return;}
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
    private void commitFilters(){if(draftFilter!=null)draftFilter.save(uiPrefs());BrowseFilter kept=draftFilter;hideFilters();draftFilter=kept;
        if(screen.equals("Home"))showHome();else if(screen.equals("Search"))showSearch();else if(screen.equals("Watchlist"))showWatchlist();
        draftFilter=null;
    }
    private void heading(String title,String subtitle){ui.gap(body,18);body.addView(ui.bold(title,32));ui.gap(body,9);TextView info=ui.text(subtitle,14,TvUi.MUTED);info.setMaxLines(3);body.addView(info);ui.gap(body,24);}
    private void notice(String title,String message){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK",null).show();}
    private Library.Addon addonFor(String id){for(Library.Addon a:Library.addons())if(a.id.equals(id))return a;return null;}
    private boolean compact(){return getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density<600;}
    private static String join(String...strings){List<String> out=new ArrayList<>();for(String s:strings)if(s!=null&&!s.isEmpty())out.add(s);return String.join("  ·  ",out);}
    private int position(Library.Title title){return prefs.getInt("position."+title.key(),0);}
    private void migrateLegacy(){
        List<Library.Title> old=new ArrayList<>(Library.titles(this));old.addAll(Library.samples());android.content.SharedPreferences.Editor edit=prefs.edit();
        for(Library.Title t:old){for(String prefix:new String[]{"saved.","review.","position."}){String before=prefix+t.id,after=prefix+t.key();if(prefs.contains(before)&&!prefs.contains(after)){Object value=prefs.getAll().get(before);if(value instanceof Boolean)edit.putBoolean(after,(Boolean)value);else if(value instanceof Integer)edit.putInt(after,(Integer)value);else if(value instanceof String)edit.putString(after,(String)value);}}}edit.apply();
    }
    private void persist(Library.Title title){try{prefs.edit().putString("title."+title.key(),title.json().toString()).apply();}catch(JSONException ignored){}}
    private List<Library.Title> allTitles(){
        Map<String,Library.Title> titles=new LinkedHashMap<>();for(Map.Entry<String,?> e:prefs.getAll().entrySet())if(e.getKey().startsWith("title."))try{Library.Title t=Library.Title.from(new JSONObject(e.getValue().toString()));titles.put(t.key(),t);}catch(Exception ignored){}
        for(ProviderRepository.Catalog c:catalogs.values())for(Library.Title t:c.titles)titles.putIfAbsent(t.key(),t);for(Library.Title t:Library.titles(this))titles.put(t.key(),t);for(Library.Title t:Library.samples())titles.put(t.key(),t);return new ArrayList<>(titles.values());
    }
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
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Catalog must be under 1 MB");out.write(b,0,n);}String json=out.toString("UTF-8");int count=Library.parse(json).size();prefs.edit().putString("catalog",json).apply();runOnUiThread(()->{if(!isDestroyed()){Toast.makeText(this,"Imported "+count+" videos",Toast.LENGTH_LONG).show();showHome();}});
        }catch(Exception e){runOnUiThread(()->{if(!isDestroyed())notice("Import failed",ProviderRepository.message(e));});}});
    }
    @Override public void onBackPressed(){if(!history.isEmpty()||!screen.equals("Home"))back();else super.onBackPressed();}
    @Override protected void onDestroy(){if(vpnListener!=null)VpnController.get(this).removeListener(vpnListener);generation++;main.removeCallbacksAndMessages(null);if(trailers!=null)trailers.release();repository.io.shutdownNow();super.onDestroy();}
}
