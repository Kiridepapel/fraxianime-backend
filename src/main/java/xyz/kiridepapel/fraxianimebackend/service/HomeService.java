package xyz.kiridepapel.fraxianimebackend.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import lombok.extern.java.Log;
import xyz.kiridepapel.fraxianimebackend.dto.HomePageDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.AnimeDataDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.ChapterDataDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.LinkDTO;
import xyz.kiridepapel.fraxianimebackend.dto.IndividualDTO.TopDataDTO;
import xyz.kiridepapel.fraxianimebackend.utils.AnimeUtils;
import xyz.kiridepapel.fraxianimebackend.utils.DataUtils;

@Service
@Log
public class HomeService {
  @Value("${APP_PRODUCTION}")
  private Boolean isProduction;
  @Value("${PROVIDER_JKANIME_URL}")
  private String providerJkanimeUrl;
  @Value("${PROVIDER_ANIMELIFE_URL}")
  private String providerAnimeLifeUrl;
  @Autowired
  private DataUtils dataUtils;
  @Autowired
  AnimeUtils animeUtils;

  @Cacheable(value = "home", key = "'animes'")
  public HomePageDTO homePage() {
    Document docAnimesJk = this.dataUtils.simpleConnect(this.providerJkanimeUrl, "Proveedor 1 inactivo");
    Document docScheduleJk = this.dataUtils.simpleConnect(this.providerJkanimeUrl + "horario", "Proveedor 1 inactivo");
    Document docAnimesLf = this.dataUtils.simpleConnect(this.providerAnimeLifeUrl, "Proveedor 2 inactivo");

    HomePageDTO animes = HomePageDTO.builder()
        .sliderAnimes(this.sliderAnimes(docAnimesJk))
        .ovasOnasSpecials(this.ovasOnasSpecials(docAnimesJk))
        .animesProgramming(this.animesProgramming(docAnimesLf, docAnimesJk))
        .nextAnimesProgramming(this.nextAnimesProgramming(docScheduleJk))
        .donghuasProgramming(this.donghuasProgramming(docAnimesJk))
        .topAnimes(this.topAnimes(docAnimesJk))
        .latestAddedAnimes(this.latestAddedAnimes(docAnimesJk))
        .latestAddedList(this.latestAddedList(docAnimesJk))
        .build();

    return animes;
  }

  public List<ChapterDataDTO> sliderAnimes(Document document) {
    Elements elements = document.select(".hero__items");
    List<ChapterDataDTO> sliderAnimes = new ArrayList<>();

    for (Element element : elements) {

      ChapterDataDTO anime = ChapterDataDTO.builder()
          .name(element.select(".hero__text h2").text())
          .imgUrl(element.attr("data-setbg"))
          .url(element.select(".hero__text a").attr("href").replace(providerJkanimeUrl, ""))
          .build();

      String[] urlSplit = anime.getUrl().split("/");
      anime.setChapter(urlSplit[urlSplit.length - 1]);

      sliderAnimes.add(anime);
    }

    return sliderAnimes;
  }

  public List<AnimeDataDTO> ovasOnasSpecials(Document document) {
    Elements elements = document.select(".solopc").last().select(".anime__item");
    List<AnimeDataDTO> ovasOnasSpecials = new ArrayList<>();

    for (Element element : elements) {
      AnimeDataDTO anime = AnimeDataDTO.builder()
          .name(element.select(".anime__item__text a").text())
          .imgUrl(element.select(".anime__item__pic").attr("data-setbg"))
          .url(element.select("a").attr("href").replace(providerJkanimeUrl, "").split("/")[0].trim())
          .type(element.select(".anime__item__text ul li").text())
          .build();

      ovasOnasSpecials.add(anime);
    }

    return ovasOnasSpecials;
  }

  public List<ChapterDataDTO> animesProgramming(Document docAnimesLf, Document docAnimesJk) {
    Elements elementsAnimeLife = docAnimesLf.body().select(".excstf").first().select(".bs");
    Elements elementsJkAnime = docAnimesJk.body().select(".listadoanime-home .anime_programing a");

    List<ChapterDataDTO> animesProgramming = new ArrayList<>();
    Map<String, ChapterDataDTO> animesJkanimes = new HashMap<>();

    // Fecha exacta con tiempo UTC y 5 horas menos si esta en produccion (Hora de
    // Perú)
    Date todayD = DataUtils.getDateNow(isProduction);
    LocalDate todayLD = DataUtils.getLocalDateTimeNow(isProduction).toLocalDate();

    // Obtener los animes de Jkanime
    String year = String.valueOf(DataUtils.getLocalDateTimeNow(isProduction).getYear());

    // Fecha en instancia de calendario
    Calendar nowCal = Calendar.getInstance();
    nowCal.setTime(todayD);

    // int daysToRest = 0;
    // daysToRest = (nowCal.get(Calendar.HOUR_OF_DAY) >= 19 &&
    // nowCal.get(Calendar.HOUR_OF_DAY) <= 23) ? 1 : 0;

    for (Element item : elementsJkAnime) {
      String date = item.select(".anime__sidebar__comment__item__text span").first().text().trim();

      // Todas las fechas se guardan en el formato dd/MM/yyyy y más adelante se
      // formatean a "Hoy", "Ayer", "Hace x días" o dd/MM
      if (date.equals("Hoy") || date.equals("Ayer")) {
        // Si es Hoy o Ayer pero actualmente es entre las 19:00 y 23:59, entonces es
        // "Hoy" en foramto dd/MM/yyyy
        if (date.equals("Hoy") || (date.equals("Ayer") && nowCal.get(Calendar.HOUR_OF_DAY) >= 19
            && nowCal.get(Calendar.HOUR_OF_DAY) <= 23)) {
          date = todayLD.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
        // Si es Ayer pero actualmente es entre las 00:00 y 18:59, entonces es "Ayer" en
        // formato dd/MM/yyyy
        else {
          date = todayLD.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
      }
      // Si no es Hoy ni Ayer, entonces es una fecha en formato dd/MM/yyyy
      else {
        date = DataUtils.parseDate(date + "/" + year, "dd/MM/yyyy", 0);
      }

      ChapterDataDTO data = ChapterDataDTO.builder()
          .name(item.select("h5").first().text().trim())
          .date(date)
          .url(item.select(".anime__sidebar__comment__item__pic img").attr("src").trim())
          .build();
      animesJkanimes.put(data.getName(), data);
    }

    // Obtener los animes de AnimeLife
    int index = 0;
    for (Element item : elementsAnimeLife) {
      String url = this.changeFormatUrl(item.select(".bsx a").attr("href"), providerAnimeLifeUrl);

      if (url.contains("/")) {
        ChapterDataDTO anime = ChapterDataDTO.builder()
            .name(item.select(".tt").first().childNodes().stream()
                .filter(node -> !(node instanceof Element && ((Element) node).tag().getName().equals("h2")))
                .map(Node::toString)
                .collect(Collectors.joining()).trim())
            .imgUrl(item.select("img").attr("src").trim().replace("?resize=247,350", ""))
            .type(item.select(".typez").text().trim().replace("TV", "Anime"))
            .chapter(item.select(".epx").text().replace("Ep 0", "").replace("Ep ", "").trim())
            .url(this.changeFormatUrl(item.select(".bsx a").attr("href"), providerAnimeLifeUrl))
            .build();

        anime = this.defineSpecialCases(anime);

        if (animesJkanimes.containsKey(anime.getName())) {
          // Si el anime está en Jkanime, usa la fecha y la imagen de Jkanime
          anime.setImgUrl(animesJkanimes.get(anime.getName()).getUrl());
          anime.setDate(this.getPastFormattedDate(animesJkanimes.get(anime.getName()).getDate()));
          anime.setState(true);
        } else {
          // Si el anime no está en Jkanime, asigna una fecha a partir de la posición
          // en del anime en la lista de animes de AnimeLife
          if (index <= 10)
            anime.setDate("Hoy");
          else if (index <= 15)
            anime.setDate("Ayer");
          else if (index <= 20)
            anime.setDate("Hace 2 días");
          else if (index <= 25)
            anime.setDate("Hace 3 días");
          anime.setState(false);
        }

        animesProgramming.add(anime);
        index++;
      }
    }

    // Ordenar por fecha y estado: [-1: a primero que b] - [1: b primero que a] -
    // [0: nada]
    animesProgramming.sort((a, b) -> {
      if (a.getDate().equals("Hoy") && b.getDate().equals("Hoy")) {
        if (!a.getState() && b.getState()) {
          return -1;
        } else if (a.getState() && !b.getState()) {
          return 1;
        } else {
          return 0;
        }
      } else if (a.getDate().equals("Hoy") && !b.getDate().equals("Hoy")) {
        return -1;
      } else if (!a.getDate().equals("Hoy") && b.getDate().equals("Hoy")) {
        return 1;
      } else {
        return a.getDate().compareTo(b.getDate());
      }
    });

    return animesProgramming;
  }

  private List<ChapterDataDTO> nextAnimesProgramming(Document document) {
    // Elimina el ultimo elemento (filtro)
    Elements elements = document.select(".box.semana");
    elements.remove(elements.size() - 1);
    List<ChapterDataDTO> nextAnimesProgramming = new ArrayList<>();

    // Obtener el indice del dia actual
    Integer startIndex = -1;
    outerloop:
    for (Element item : elements) {
      Elements animesDay = item.select(".cajas .box");
      for (Element subItem : animesDay) {
        String date = this.formattedNextDate(subItem.select(".last time").text().split(" ")[0]);

        // Verificar si la fecha es hoy
        LocalDate ld = DataUtils.getLocalDateTimeNow(isProduction).toLocalDate();
        boolean isToday = date.equals(ld.format(DateTimeFormatter.ofPattern("dd/MM")));
        
        if (isToday) {
          startIndex = elements.indexOf(item);
          break outerloop;
        }
      }
    }

    List<ChapterDataDTO> tempLastAnimes = new ArrayList<>();
    int index = 0;
    for (Element item : elements) {
      String day = DataUtils.removeDiacritics(item.select("h2").text());
      Elements animesDay = item.select(".cajas .box");

      for (Element subItem : animesDay) {
        String url = subItem.select("a").first().attr("href");
        String date = this.formattedNextDate(subItem.select(".last time").text().split(" ")[0]);
        String time = subItem.select(".last time").text().split(" ")[1];

        ChapterDataDTO anime = ChapterDataDTO.builder()
            .name(subItem.select("a").first().select("h3").text())
            .imgUrl(subItem.select(".boxx img").attr("src"))
            .type("Anime")
            .chapter(this.nextChapterBasenOnDate(date, day, subItem.select(".last span").text().split(":")[1].trim()))
            .date(date)
            .time(time.substring(0, time.length() - 3))
            .url(url.substring(0, url.length() - 1))
            .build();

        anime.setName(this.animeUtils.specialNameOrUrlCases(anime.getName(), 'h'));
        anime.setUrl(this.animeUtils.specialNameOrUrlCases(anime.getUrl(), 'h'));
        
        if (index < startIndex) {
          tempLastAnimes.add(anime);
        } else {
          nextAnimesProgramming.add(anime);
        }
      }

      index++;
    }

    nextAnimesProgramming.addAll(tempLastAnimes);

    return nextAnimesProgramming;
  }

  private String nextChapterBasenOnDate(String dateTime, String day, String chapter) {
    Integer intChapter = Integer.parseInt(chapter);
    // dateTime: dd/MM

    return String.valueOf(intChapter);
  }

  private String formattedNextDate(String recivedDate) {
    String date = this.animeUtils.calcNextChapterDateSchedule(recivedDate, this.isProduction);
    return date;
  }

  public List<ChapterDataDTO> donghuasProgramming(Document document) {
    Elements elementsJkAnime = document.select(".donghuas_programing a.bloqq");
    List<ChapterDataDTO> lastChapters = new ArrayList<>();

    String year = String.valueOf(LocalDate.now().getYear());
    for (Element item : elementsJkAnime) {
      String date = item.select(".anime__sidebar__comment__item__text span").first().text().trim();

      if (date.equals("Hoy") || date.equals("Ayer")) {
        date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      } else {
        date = DataUtils.parseDate(date + "/" + year, "dd/MM/yyyy", 0);
      }

      ChapterDataDTO anime = ChapterDataDTO.builder()
          .name(item.select(".anime__sidebar__comment__item__text h5").text())
          .imgUrl(item.select(".anime__sidebar__comment__item__pic img").attr("src"))
          .chapter(item.select(".anime__sidebar__comment__item__text h6").text().replace("Episodio", "Capitulo"))
          .type("Donghua")
          .date(this.getPastFormattedDate(date))
          .url(item.select("a").attr("href").replace(providerJkanimeUrl, ""))
          .state(true)
          .build();

      // Quitar el "/" final de la url
      if (anime.getUrl().endsWith("/")) {
        anime.setUrl(anime.getUrl().substring(0, anime.getUrl().length() - 1));
      }

      anime.setName(this.animeUtils.specialNameOrUrlCases(anime.getName(), 'h'));
      anime.setUrl(this.animeUtils.specialNameOrUrlCases(anime.getUrl(), 'h'));

      lastChapters.add(anime);
    }

    return lastChapters;
  }

  public List<TopDataDTO> topAnimes(Document document) {
    Element data = document.select(".destacados").last().select(".container div").first();
    List<TopDataDTO> topAnimes = new ArrayList<>(10);

    Element firstTop = data.child(2);
    TopDataDTO firstAnime = TopDataDTO.builder()
        .name(firstTop.select(".comment h5").text())
        .imgUrl(firstTop.select(".anime__item__pic").attr("data-setbg"))
        .likes(Integer.parseInt(firstTop.select(".vc").text().trim()))
        .position(Integer.parseInt(firstTop.select(".ep").text().trim()))
        .url(firstTop.select("a").attr("href").replace(providerJkanimeUrl, ""))
        .build();
    topAnimes.add(firstAnime);

    Elements restTop = data.child(3).select("a");
    for (Element element : restTop) {
      TopDataDTO anime = TopDataDTO.builder()
          .name(element.select(".comment h5").text())
          .imgUrl(element.select(".anime__item__pic__fila4").attr("data-setbg"))
          .likes(Integer.parseInt(element.select(".vc").text()))
          .position(Integer.parseInt(element.select(".anime__item__pic__fila4 div").first().text().trim()))
          .url(element.attr("href").replace(providerJkanimeUrl, ""))
          .build();

      topAnimes.add(anime);
    }

    return topAnimes;
  }

  public List<AnimeDataDTO> latestAddedAnimes(Document document) {
    Elements elements = document.select(".trending__anime .anime__item");
    List<AnimeDataDTO> latestAddedAnimes = new ArrayList<>();

    for (Element element : elements) {
      AnimeDataDTO anime = AnimeDataDTO.builder()
          .name(element.select(".anime__item__text h5 a").text())
          .imgUrl(element.select(".anime__item__pic").attr("data-setbg"))
          .url(element.select("a").attr("href").replace(providerJkanimeUrl, ""))
          .state(element.select(".anime__item__text ul li").first().text())
          .type(element.select(".anime__item__text ul li").last().text())
          .build();

      if (!anime.getType().equals("ONA")) {
        latestAddedAnimes.add(anime);
      }
    }

    return latestAddedAnimes;
  }

  public List<LinkDTO> latestAddedList(Document document) {
    Elements elements = document.select(".trending_div .side-menu li");
    List<LinkDTO> latestAddedList = new ArrayList<>();

    for (Element element : elements) {
      LinkDTO anime = LinkDTO.builder()
          .name(element.select("a").text())
          .url(element.select("a").attr("href").replace(providerJkanimeUrl, ""))
          .build();

      latestAddedList.add(anime);
    }

    return latestAddedList;
  }

  private String getPastFormattedDate(String dateText) {
    if (dateText.matches("^\\d{1,2}/\\d{1,2}/\\d{4}$")) {
      // Manejar el caso de que la fecha sea DIA/MES/AÑO
      LocalDate date = LocalDate.parse((dateText), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
      long daysBetween = ChronoUnit.DAYS.between(date, LocalDate.now());

      if (daysBetween <= 7) {
        if (daysBetween == 0) {
          return "Hoy";
        } else if (daysBetween == 1) {
          return "Ayer";
        } else {
          return "Hace " + daysBetween + " días";
        }
      } else {
        String[] dateArray = dateText.split("/");
        return dateArray[0] + "/" + dateArray[1];
      }
    } else {
      return dateText;
    }
  }

  private ChapterDataDTO defineSpecialCases(ChapterDataDTO anime) {
    // Elimina caracteres raros del nombre
    anime.setName(anime.getName().trim().replace("“", String.valueOf('"')).replace("”", String.valueOf('"')));
    anime.setName(this.animeUtils.specialNameOrUrlCases(anime.getName(), 'h')); // Nombres especiales
    anime.setName(anime.getName().replace("Movie", "").trim()); // "Movie" en el nombre
    anime.setUrl(this.animeUtils.specialNameOrUrlCases(anime.getUrl(), 'h')); // Urls especiales

    // Si el número de capítulo tiene un "."
    if (anime.getChapter().contains(".")) {
      int chapter = Integer.parseInt(anime.getChapter().split("\\.")[0]) + 1;

      // Reconstruye la url sin el capítulo
      String[] urlSplit = anime.getUrl().split("-");
      String url = "";
      for (int i = 0; i < urlSplit.length - 1; i++) {
        url += (i != 0) ? "-" + urlSplit[i] : urlSplit[i];
      }

      // Asigna el nuevo capítulo y la nueva url
      anime.setChapter(String.valueOf(chapter));
      anime.setUrl(url + "/" + chapter);
    }

    return anime;
  }

  private String changeFormatUrl(String url, String providerUrl) {
    String newUrl = url.replace(providerUrl, "").replaceAll("-(0*)(\\d+)/?$", "/$2");
    // Verifica si la URL termina con el patrón -xx-2
    if (url.matches(".*-\\d{2}-2/?$")) {
      // Extrae el número y lo incrementa
      String numberPart = url.replaceAll("^.*-(\\d{2})-2/?$", "$1");
      try {
        int number = Integer.parseInt(numberPart) + 1;
        newUrl = url.replaceFirst("-\\d{2}-2/?$", "/" + number);
        return newUrl.replace(providerUrl, "");
      } catch (NumberFormatException e) {
        log.warning("Error parsing number from URL: " + url);
      }
    } else {
      // Si no termina con -xx-2, solo elimina los ceros a la izquierda
      newUrl = url.replace(providerUrl, "").replaceAll("-(0*)(\\d+)/?$", "/$2");
      return newUrl;
    }

    return newUrl;
  }

}
