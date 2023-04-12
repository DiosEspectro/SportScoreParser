import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SportScoreParser {
    static String getBasicUrl(){ return "https://www.championat.com/"; }
    static String getTodayDate(){
        Date dateNow = new Date();
        SimpleDateFormat formatForDate = new SimpleDateFormat("yyyy-MM-dd");
        return formatForDate.format(dateNow);
    }
    static URL getTodayUrl(){
        //Date dateNow = new Date();
        //SimpleDateFormat formatForDate = new SimpleDateFormat("yyyy-MM-dd");
        //return getBasicUrl() + "stat/" + formatForDate.format(dateNow) + ".json";
        URL url = null;

        try {
            url = new URL(getBasicUrl() + "stat/" + getTodayDate() + ".json");
        }catch(MalformedURLException e){
            e.printStackTrace();
        }
        return url;
    }

    public static void main(String[] args) {
        for (TournamentEnum tour : TournamentEnum.values()) {
            parseTourAllMatches(tour);
            parseTourTodayMatches(tour);
        }

        //parseTourTodayMatches(TournamentEnum.CHAMPIONSLEAGUE);
        //System.out.println(getTodayUrl());
    }

    static void parseTourAllMatches(TournamentEnum tour){
        String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
        String url = getBasicUrl()+getSportNameByTour(tour).toString().toLowerCase()+"/"+getTourChampId(tour)+"/tournament/"+tourInfo[1]+"/calendar/";

        try {
            Document doc = Jsoup.connect(url).get();
            Elements elms = doc.select("table.stat-results__table > tbody > tr"); // Получаем список матчей

            int matchesCount = elms.size();
            String[][] matches = new String[matchesCount][21];

            for(int i=0;i<matchesCount;i++){
                String strtmp = "";
                String[] strsplit;

                Element matchRaw = elms.get(i); // Необработанная строка с матчем
                //System.out.println(matchRaw.attributes().get("data-team"));

                // Сперва запишем параметры, которые можем получить из аттрибутов заглавной строки матча
                matches[i][2] = matchRaw.attributes().get("data-round"); // ID этапа турнира
                matches[i][3] = matchRaw.attributes().get("data-tour"); // № тура
                matches[i][4] = matchRaw.attributes().get("data-month"); // Год_месяц матча
                matches[i][5] = matchRaw.attributes().get("data-team").split("/")[0]; // Id домашней команды
                matches[i][6] = matchRaw.attributes().get("data-team").split("/")[1]; // Id гостевой команды
                matches[i][7] = matchRaw.attributes().get("data-played"); // Признак - сыгран ли матч (0 - нет, 1 - да)

                // Теперь идём вглубь, в столбцы
                Element tmp = matchRaw.selectFirst("tr > td.stat-results__fav");
                if(tmp != null) matches[i][0] = tmp.attributes().get("data-type").trim(); // Тип данных (например, match)

                tmp = matchRaw.selectFirst("tr > td.stat-results__fav");
                if(tmp != null) matches[i][1] = tmp.attributes().get("data-id").trim(); // ID матча

                tmp = matchRaw.selectFirst("tr > td.stat-results__link > a");
                if(tmp != null) matches[i][8] = tmp.attributes().get("href").trim(); // Ссылка на матч

                tmp = matchRaw.selectFirst("tr > td.stat-results__group");
                if(tmp != null) matches[i][9] = tmp.text().trim(); // Название группы

                tmp = matchRaw.selectFirst("tr > td.stat-results__tour-num");
                if(tmp != null) matches[i][10] = tmp.text().trim(); // Номер тура

                // Далее обрабатываем дату и время матча
                tmp = matchRaw.selectFirst("tr > td.stat-results__date-time");
                if(tmp != null)
                {
                    strtmp = tmp.text().trim(); // Строка с датой и временем
                    strsplit = strtmp.replace("                            &nbsp;", " ").split(" ");

                    matches[i][11] = transformDate(strsplit[0]); // Дата матча
                    if(strsplit.length > 1) matches[i][12] = strsplit[1]; // Время матча
                }

                // Далее обрабатываем названия команд
                Elements tmps = matchRaw.select("span.table-item__name");
                if(tmps.size() == 2) {
                    matches[i][13] = tmps.get(0).text(); // Название команды 1
                    matches[i][14] = tmps.get(1).text(); // Название команды 2
                }

                // Далее счёт, если есть (если есть признак сыгранного матча)
                if(matches[i][7].equals("1")) {
                    tmp = matchRaw.selectFirst("tr > td.stat-results__count > a > span.stat-results__count-main");
                    if (tmp != null) {
                        strsplit = tmp.text().trim().split(":");
                        if (strsplit.length == 2) {
                            matches[i][15] = strsplit[0].trim(); // Забитые голы первой команды
                            matches[i][16] = strsplit[1].trim(); // Забитые голы второй команды
                        }
                    }

                    tmp = matchRaw.selectFirst("tr > td.stat-results__count > a > span.stat-results__count-ext");
                    if (tmp != null) {
                        strsplit = tmp.text().trim().split(":");
                        if (strsplit.length == 2) {
                            matches[i][17] = strsplit[0].trim(); // Экстра-голы первой команды
                            matches[i][18] = strsplit[1].trim(); // Экстра-голы второй команды
                        } else {
                            matches[i][17] = strsplit[0].trim(); // Если это не счёт, а просто текст или символы
                            matches[i][18] = strsplit[0].trim(); // То записываем их в обе ячейки
                        }
                    }
                }
            }

            saveMatchesToXML(tour, tourInfo, matches, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void saveMatchesToXML(TournamentEnum tour, String[] tourInfo, String[][] matches, boolean onlyToday){
        String fileName = SportScoreParser.getSportNameByTour(tour).toString().toLowerCase() + "_" + tour.toString().toLowerCase();
        if(onlyToday) fileName = getTodayDate()+"_"+fileName;
        fileName +=  ".xml";

            //fileName = SportScoreParser.getSportNameByTour(tour).toString().toLowerCase() + "_" + tour.toString().toLowerCase() + ".xml";

        String filePath = "src/xml/" + fileName;

        org.w3c.dom.Element elmnt;
        org.w3c.dom.Element matchesroot;
        org.w3c.dom.Element match;

        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

            // Создание корневого элемента
            org.w3c.dom.Document doc = docBuilder.newDocument();
            org.w3c.dom.Element rootElement = doc.createElement("tournament");
            doc.appendChild(rootElement);

            // Далее прописываем параметры турнира (ID, название и.т.д)
            elmnt = doc.createElement("tourid");
            elmnt.appendChild(doc.createTextNode(tourInfo[0])); // Длинный ID
            rootElement.appendChild(elmnt);

            elmnt = doc.createElement("touridshort");
            elmnt.appendChild(doc.createTextNode(tourInfo[1])); // Короткий ID
            rootElement.appendChild(elmnt);

            elmnt = doc.createElement("tourname");
            elmnt.appendChild(doc.createTextNode(tourInfo[2])); // Название турнира
            rootElement.appendChild(elmnt);

            // Далее создаём коллекцию матчей
            matchesroot = doc.createElement("matches");
            rootElement.appendChild(matchesroot);

            // Далее добавляем каждый матч в отдельную группу
            int matchesCount = matches.length;
            for (String[] strings : matches) {
                if (strings[5].equals("0") || strings[6].equals("0"))
                    continue; // Если хотя бы одна команда не указана, то не записываем

                // Создаём матч
                match = doc.createElement("match");
                matchesroot.appendChild(match);

                // Далее добавляем все параметры матча вовнутрь матча
                for (int j = 0; j < 21; j++) {
                    String tagName = switch (j) {
                        case 0 -> "type";
                        case 1 -> "id";
                        case 2 -> "round";
                        case 3 -> "tour";
                        case 4 -> "month";
                        case 5 -> "team1id";
                        case 6 -> "team2id";
                        case 7 -> "played";
                        case 8 -> "link";
                        case 9 -> "group";
                        case 10 -> "tourstr";
                        case 11 -> "date";
                        case 12 -> "time";
                        case 13 -> "team1";
                        case 14 -> "team2";
                        case 15 -> "score1";
                        case 16 -> "score2";
                        case 17 -> "score1ext";
                        case 18 -> "score2ext";
                        case 19 -> "live";
                        case 20 -> "liveperiod";
                        default -> "";
                    };

                    if (strings[j] != null) {
                        elmnt = doc.createElement(tagName);
                        elmnt.appendChild(doc.createTextNode(strings[j])); // ID
                        match.appendChild(elmnt);
                    }
                }
            }

            // Запись XML-файла
            doc.setXmlStandalone(true);
            doc.normalizeDocument();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(filePath));
            transformer.transform(source, result);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    static void parseTourTodayMatches(TournamentEnum tour) {
        String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
        SportNameEnum sportName = getSportNameByTour(tour);

        URL url = getTodayUrl();

        try{
            //Object object = new JSONParser().parse(new FileReader(url));
            //JSONObject jsonObject = (JSONObject) object;
            JSONParser parser = new JSONParser();
            Object obj = parser.parse(new InputStreamReader(url.openStream()));
            JSONObject jsonObject = (JSONObject) obj;
            JSONObject matchesObj = (JSONObject) jsonObject.get("matches");
            if(matchesObj != null) {
                matchesObj = (JSONObject) matchesObj.get(sportName.toString().toLowerCase(Locale.ROOT));

                if(matchesObj != null) {
                    matchesObj = (JSONObject) matchesObj.get("tournaments");

                    if(matchesObj != null) {
                        matchesObj = (JSONObject) matchesObj.get(tourInfo[0]);

                        if(matchesObj != null) {
                            JSONArray elms = (JSONArray) matchesObj.get("matches");

                            int matchesCount = elms.size();

                            if (matchesCount > 0) {
                                String[][] matches = new String[matchesCount][21];
                                JSONObject match_more;
                                JSONArray match_arr;

                                // Поехали собирать информацию
                                int i = -1;
                                boolean isLive;

                                for(Object o:elms) {
                                    i++;
                                    isLive = false;

                                    JSONObject match = (JSONObject) o;
                                    matches[i][0] = "match"; // type
                                    matches[i][1] = match.get("data-id").toString(); // id

                                    match_more = (JSONObject) match.get("group");
                                    matches[i][2] = match_more.get("stage").toString() + "/" + match_more.get("id").toString(); // round
                                    matches[i][9] = match_more.get("name").toString(); // group

                                    matches[i][3] = match.get("tour").toString(); // tour
                                    matches[i][10] = matches[i][3]; // tourstr

                                    match_arr = (JSONArray) match.get("teams");
                                    match_more = (JSONObject) match_arr.get(0);
                                    matches[i][5] = match_more.get("id").toString(); // team1id
                                    matches[i][13] = match_more.get("name").toString(); // team1

                                    match_more = (JSONObject) match_arr.get(1);
                                    matches[i][6] = match_more.get("id").toString(); // team2id
                                    matches[i][14] = match_more.get("name").toString(); // team2

                                    match_more = (JSONObject) match.get("flags");
                                    matches[i][7] = match_more.get("is_played").toString(); // played
                                    if(matches[i][7].equals("0")) // если матч не завершён, проверяем - идёт-ли матч прямо сейчас
                                        isLive = ((long)match_more.get("live") != 0);

                                    matches[i][8] = match.get("link").toString(); // link

                                    matches[i][11] = match.get("date").toString(); // date
                                    matches[i][12] = match.get("time").toString(); // time

                                    String[] date = matches[i][11].split("-");
                                    matches[i][4] = date[0] + "_" + date[1]; // month

                                    if(matches[i][7].equals("1") || isLive) { // Если матч завершён, либо матч идёт прямо сейчас
                                        match_more = (JSONObject) match.get("result");
                                        match_more = (JSONObject) match_more.get("detailed");

                                        matches[i][15] = match_more.get("goal1").toString(); // score1
                                        matches[i][16] = match_more.get("goal2").toString(); // score2
                                        matches[i][17] = match_more.get("extra").toString(); // score1ext
                                        matches[i][18] = matches[i][17]; // score2ext

                                        if (isLive) {
                                            matches[i][19] = "1"; // live
                                            match_more = (JSONObject) match.get("status");
                                            matches[i][20] = match_more.get("name").toString(); // liveperiod
                                        }
                                    }
                                }

                                saveMatchesToXML(tour, tourInfo, matches, true);
                            }
                        }
                    }
                }
            }
            //JSONArray jsonArray = (JSONArray) jsonObject.get("matches");
/*
            for(Object o:jsonArray){
                JSONObject book = (JSONObject) o;
                System.out.println("\nТекущий элемент: book");
                System.out.println("Название книги: " + book.get("title"));
                System.out.println("Автор: " + book.get("author"));
                System.out.println("Год издания: " + book.get("year"));
            }*/
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    static String[] getCurTourInfo(TournamentEnum tour){
        // Возвращает массив строк, 0 - это длинный ID текущего турнира для Live-парсинга; 1 - это короткий ID для ссылок; 2 - Имя турнира
        String[] ret = new String[3];
        String url = getBasicUrl()+getSportNameByTour(tour).toString().toLowerCase()+"/"+getTourChampId(tour)+".html"; // Генерим ссылку

        try {
            Document doc = Jsoup.connect(url).get();
            Elements elms = doc.select("div.tournament-top");
            String[] classes = elms.get(0).className().split(" ");
            ret[0] = classes[1].substring(1) ;// Длинный ID
            String[] values = classes[1].split("-");
            ret[1] = values[1]; // Короткий ID

            elms = doc.select("div.entity-header__title-name a");
            ret[2] = elms.get(0).text(); // Название турнира
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    static SportNameEnum getSportNameByTour(TournamentEnum tournament){
        return switch (tournament) {
            case RPL, CHAMPIONSLEAGUE, WORLDCUP -> SportNameEnum.FOOTBALL;
            case KHL, WHC -> SportNameEnum.HOCKEY;
        };
    }

    static String getTourChampId(TournamentEnum tournament){
        return switch (tournament) {
            case RPL -> "_russiapl";
            case CHAMPIONSLEAGUE -> "_ucl";
            case WORLDCUP -> "_worldcup";
            case KHL -> "_superleague";
            case WHC -> "_whc";
        };
    }

    static String transformDate(String date){
        String[] e = date.split("\\.");
        return e[2] + "-" + e[1] + "-" + e[0];
    }
}
