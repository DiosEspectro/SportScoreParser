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

public class SportScoreParser {
    static String getBasicUrl(){ return "https://www.championat.com/"; }

    public static void main(String[] args) {
        for (TournamentEnum tour : TournamentEnum.values()) {
            parseTourAllMatches(tour);
        }
    }

    static void parseTourAllMatches(TournamentEnum tour){
        String[] tourInfo = getCurTourInfo(tour); // Получаем информацию о турнире
        String url = getBasicUrl()+getSportNameByTour(tour).toString().toLowerCase()+"/"+getTourChampId(tour)+"/tournament/"+tourInfo[1]+"/calendar/";

        try {
            Document doc = Jsoup.connect(url).get();
            Elements elms = doc.select("table.stat-results__table > tbody > tr"); // Получаем список матчей

            int matchesCount = elms.size();
            String[][] matches = new String[matchesCount][19];

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

            saveMatchesToXML(tour, tourInfo, matches);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void saveMatchesToXML(TournamentEnum tour, String[] tourInfo, String[][] matches){
        String fileName = SportScoreParser.getSportNameByTour(tour).toString().toLowerCase() + "_" + tour.toString().toLowerCase() + ".xml";
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
                for (int j = 0; j < 19; j++) {
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
