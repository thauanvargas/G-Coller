import gearth.extensions.ExtensionForm;
import gearth.extensions.ExtensionInfo;
import gearth.extensions.parsers.*;
import gearth.protocol.HMessage;
import gearth.protocol.HPacket;
import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import org.json.JSONObject;

import javax.swing.Timer;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

@ExtensionInfo(
        Title = "AutoColler",
        Description = "discord: thauan#7604",
        Version = "1.4",
        Author = "Thauan"
)

public class GColler extends ExtensionForm implements Initializable {
    public static GColler RUNNING_INSTANCE;
    public CheckBox checkEnabled;
    public CheckBox checkCoords;
    public CheckBox checkAutoPlay;
    public Label labelRoomLoaded;
    public Label labelRoomInfo;
    public Label labelChair1;
    public Label labelChair2;
    public Label labelChair3;
    public Label labelChair4;
    public int chairChooser = 1;
    public CheckBox checkBanzaiBug;
    public List<Integer> listBanzai = new LinkedList<>();

    public List<Integer> listColorTiles = new LinkedList<>();

    public List<HPoint> coordinatesGame = new LinkedList<>();

    public List<HPoint> coordinateChairs = new LinkedList<>();

    public List<HPoint> coordinatesColorTiles = new LinkedList<>();

    public TreeMap<Integer, HPoint> usersCoordinates = new TreeMap<>();
    public Label habboName;
    public Label labelInfo;
    public PasswordField password;
    public TextField email;
    HPoint fireGateCoord;
    HPoint treadmillCoord;

    public boolean enabled = false;
    public boolean isTeleporting = false;

    public boolean isSitted = false;

    public boolean sentCommand = false;

    public boolean inAColorTile = false;

    public boolean inABanzai = false;

    TreeMap<Integer,HPoint> floorItemsID_HPoint = new TreeMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
    public String host;
    public String currentDirection;
    public int currentX;
    public int currentY;
    public int habboId;
    public String habboUserName;
    public int habboIndex = -1;
    List<Integer> banzaiToRemove = new ArrayList<>();
    Timer timerSitFast = new Timer(25, e -> handleSit());
    Timer timerPlay = new Timer(20, e -> handlePlay());
    Timer timerFixBugs = new Timer(1000, e -> handleBug());

    @Override
    protected void onStartConnection() {
        new Thread(() -> {
            timerSitFast.setRepeats(true);
            timerPlay.setRepeats(true);
            timerFixBugs.setRepeats(true);
        }).start();
    }


    @Override
    protected void onShow() {
        System.out.println("AutoColler Inicializou!");
        if(!enabled) {
            new Thread(() -> {
                sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
                sendToServer(new HPacket("AvatarExpression", HMessage.Direction.TOSERVER, 0));
                sendToServer(new HPacket("GetHeightMap", HMessage.Direction.TOSERVER));
            }).start();
            checkEnabled.setDisable(false);
            checkAutoPlay.setDisable(false);
            checkBanzaiBug.setDisable(false);
            checkCoords.setDisable(false);
        }
    }

    @Override
    protected void initExtension() {
        RUNNING_INSTANCE = this;

        onConnect((host, port, APIVersion, versionClient, client) -> {
            this.host = host.substring(5, 7);
        });

        intercept(HMessage.Direction.TOCLIENT, "UserObject", hMessage -> {
            habboId = hMessage.getPacket().readInteger();
            habboUserName = hMessage.getPacket().readString();
            Platform.runLater(() -> habboName.setText("Teu nome: " + habboUserName));
        });

        intercept(HMessage.Direction.TOSERVER, "GetGuestRoom", hMessage -> {
            if (habboUserName == null) {
                sendToServer(new HPacket("InfoRetrieve", HMessage.Direction.TOSERVER));
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "Expression", hMessage -> {
            if(primaryStage.isShowing() && habboIndex == -1){
                habboIndex = hMessage.getPacket().readInteger();
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "Whisper", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            hPacket.readInteger();
            String msg = hPacket.readString();
            if(msg.contains("AutoCLR")) {
                hMessage.setBlocked(true);
            }
        });

        intercept(HMessage.Direction.TOSERVER, "Chat", HMessage -> {
            HPacket hPacket = HMessage.getPacket();
            String msg = hPacket.readString();

            if(msg.equals("perdy")) {
                checkBanzaiBug.setSelected(false);
                checkAutoPlay.setSelected(false);
                if(timerPlay.isRunning()) timerPlay.stop();
                if(timerSitFast.isRunning()) timerSitFast.stop();
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "Users", hMessage -> {
            try {
                HPacket hPacket = hMessage.getPacket();
                HEntity[] roomUsersList = HEntity.parse(hPacket);
                for (HEntity hEntity: roomUsersList){
                    if(hEntity.getName().equals(habboUserName)){
                        habboIndex = hEntity.getIndex();
                    }
                    if(hEntity.getName().equals("Erotico") || hEntity.getName().contains("thauan")) {
                        sendToServer(new HPacket("Whisper", HMessage.Direction.TOSERVER, hEntity.getName() + " To de AutoCLR.", 1007));
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
        intercept(HMessage.Direction.TOCLIENT, "AvatarEffect", hMessage -> {
            HPacket hPacket = hMessage.getPacket();
            if(hPacket.readInteger() == habboIndex) {
                HPoint currentHPoint = new HPoint(currentX, currentY);
                if(!coordinatesGame.contains(currentHPoint)) {
                    sentCommand = false;
                }
            }
        });

        intercept(HMessage.Direction.TOSERVER, "OpenFlatConnection", hMessage -> {
            Platform.runLater(() -> {
                labelRoomLoaded.setText("Sim");
                labelRoomLoaded.setTextFill(Color.GREEN);
            });
        });

        intercept(HMessage.Direction.TOCLIENT, "UserUpdate", hMessage -> {
            if(checkEnabled.isSelected()) {
                for (HEntityUpdate hEntityUpdate : HEntityUpdate.parse(hMessage.getPacket())) {
                    HStance hStance = hEntityUpdate.getStance();
                    int CurrentIndex = hEntityUpdate.getIndex();
                    if (habboIndex == CurrentIndex) {
                        currentDirection = hEntityUpdate.getBodyFacing().toString();
                        currentX = hEntityUpdate.getTile().getX();
                        currentY = hEntityUpdate.getTile().getY();

                        HPoint userHpoint = new HPoint(currentX, currentY);

                        if(userHpoint.equals(treadmillCoord))
                            if(timerPlay.isRunning()) { timerPlay.stop(); }

                        usersCoordinates.put(CurrentIndex, userHpoint);

                        if(coordinatesColorTiles.contains(userHpoint)) {
                            inAColorTile = true;
                        }else {
                            inAColorTile = false;
                        }

                        if(coordinatesGame.contains(userHpoint) && !sentCommand && checkBanzaiBug.isSelected()) {
                            inABanzai = true;
                            sentCommand = true;
                                sendToServer(new HPacket("Chat", HMessage.Direction.TOSERVER, "bug perdy", 0, 0));
                        } else {
                            inABanzai = false;
                        }

                        if(sentCommand && !isTeleporting && checkBanzaiBug.isSelected()) {
                            for(HPoint coords : coordinatesGame) {
                                sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, coords.getX(), coords.getY()));
                            }
                            new Thread(() -> {
                                Delay(500);
                                isTeleporting = true;
                            }).start();
                        }

                        if(isTeleporting && checkBanzaiBug.isSelected()) {
                            new Thread(() -> {
                                Delay(500);
                                timerSitFast.start();
                            }).start();
                        }

                        if(hStance == HStance.Sit) {
                            isSitted = true;
                            isTeleporting = false;
                            sentCommand = false;
                            usersCoordinates.clear();
                            if(checkAutoPlay.isSelected() && !timerPlay.isRunning())
                                timerPlay.start();
                        }

                        if(!inABanzai && !inAColorTile) {
                            if(timerSitFast.isRunning()){ timerSitFast.stop();}
                        }

                    }else{
                        int habboX = hEntityUpdate.getTile().getX();
                        int habboY = hEntityUpdate.getTile().getY();
                        HPoint hPoint = new HPoint(habboX, habboY);

                        if(coordinatesGame.contains(hPoint)) {
                            usersCoordinates.put(CurrentIndex, hPoint);
                        }
                    }
                }
            }
        });

        intercept(HMessage.Direction.TOCLIENT, "Objects", hMessage -> {
            try {
                listBanzai.clear();
                listColorTiles.clear();
                banzaiToRemove.clear();
                floorItemsID_HPoint.clear();
                sentCommand = false;
                isTeleporting = false;
                for (HFloorItem hFloorItem : HFloorItem.parse(hMessage.getPacket())) {
                    HPoint hPoint = new HPoint(hFloorItem.getTile().getX(), hFloorItem.getTile().getY(), hFloorItem.getTile().getZ());
                    if(!floorItemsID_HPoint.containsKey(hFloorItem.getId()))
                        floorItemsID_HPoint.put(hFloorItem.getId(), hPoint);
                    if(hFloorItem.getTypeId() == 3378) {
                        listBanzai.add(hFloorItem.getId());
                    }
                    if(hFloorItem.getTypeId() == 3423){
                        listColorTiles.add(hFloorItem.getId());
                    }
                    if(hFloorItem.getTypeId() == 3432) {
                        fireGateCoord = hPoint;
                    }
                    if(hFloorItem.getTypeId() == 8798) {
                        treadmillCoord = hPoint;
                    }
                }
                for (Integer banzaiId : listBanzai) {
                    int coordXBanzai = floorItemsID_HPoint.get(banzaiId).getX();
                    int coordYBanzai = floorItemsID_HPoint.get(banzaiId).getY();

                    for (Integer colorId : listColorTiles) {
                    int coordXColorTile = floorItemsID_HPoint.get(colorId).getX();
                    int coordYColorTile = floorItemsID_HPoint.get(colorId).getY();
                        if(coordXBanzai == coordXColorTile && coordYBanzai == coordYColorTile) {
                            banzaiToRemove.add(banzaiId);
                            coordinatesColorTiles.add(new HPoint(coordXColorTile, coordYColorTile));
                        }
                    }
                }
                listBanzai.removeAll(banzaiToRemove);
                for(Integer banzaiId : listBanzai) {
                    int coordXBanzai = floorItemsID_HPoint.get(banzaiId).getX();
                    int coordYBanzai = floorItemsID_HPoint.get(banzaiId).getY();
                    coordinatesGame.add(new HPoint(coordXBanzai, coordYBanzai));
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        });

        intercept(HMessage.Direction.TOSERVER, "MoveAvatar", hMessage -> {
            if(checkCoords.isSelected()){
                int XCoord = hMessage.getPacket().readInteger();
                int YCoord = hMessage.getPacket().readInteger();
                Platform.runLater(() -> checkCoords.setText("Marcar Cordenadas dos Bancos"));
                coordinateChairs.add(new HPoint(XCoord, YCoord));
                if(chairChooser == 1)
                    Platform.runLater(() -> labelChair1.setText("Cadeira 1 - (X: " + XCoord + ", Y: " + YCoord + ")"));
                else if(chairChooser == 2)
                    Platform.runLater(() -> labelChair2.setText("Cadeira 2 - (X: " + XCoord + ", Y: " + YCoord + ")"));
                else if(chairChooser == 3)
                    Platform.runLater(() -> labelChair3.setText("Cadeira 3 - (X: " + XCoord + ", Y: " + YCoord + ")"));
                else if(chairChooser == 4){
                    Platform.runLater(() -> labelChair4.setText("Cadeira 4 - (X: " + XCoord + ", Y: " + YCoord + ")"));
                    checkCoords.setSelected(false);
                } else {
                    coordinateChairs.clear();
                    chairChooser = 0;
                    Platform.runLater(() -> {
                        checkCoords.setText("Resetei as cordenadas.") ;
                        labelChair1.setText("");
                        labelChair2.setText("");
                        labelChair3.setText("");
                        labelChair4.setText("");
                    });
                }
                chairChooser++;
                hMessage.setBlocked(true);
            }
        });

    }

    public void handleSit() {
        int shortestDistance = distSquaredOfHabbo(coordinateChairs.get(0));
        HPoint closest = coordinateChairs.get(0);
        for(HPoint coordsChair : coordinateChairs) {

            int d = distSquaredOfHabbo(coordsChair);
            if (d < shortestDistance) {
                closest = coordsChair;
                shortestDistance = d;
            }

        }
        HPoint finalClosest = closest;
        new Thread(() -> {
            sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, finalClosest.getX(), finalClosest.getY()));
        }).start();
    }

    public void handlePlay() {
        if(!inAColorTile && (isSitted || timerPlay.isRunning())) {
            for(HPoint coords : coordinatesGame) {
                if(!usersCoordinates.containsValue(coords)) {
                    System.out.println(coords);
                    new Thread(() -> {
                        sendToServer(new HPacket("MoveAvatar", HMessage.Direction.TOSERVER, coords.getX(), coords.getY()));
                    }).start();
                    break;
                }
            }
        }else {
            handleSit();
        }
    }

    public int distSquaredOfHabbo(HPoint point) {
        int diffX = currentX - point.getX();
        int diffY = currentY - point.getY();
        return (diffX * diffX + diffY * diffY);
    }

    public void handleBug() {
//        if(previousX == currentX && previousY == currentY && !inAColorTile) {
//            if(timerSitFast.isRunning()){ timerSitFast.stop();}
//            sentCommand = true;
//            isTeleporting = false;
//            handlePlay();
//        }
//        previousX = currentX;
//        previousY = currentY;
    }

    public void Delay(int time){
        try {
            Thread.sleep(time);
        } catch (InterruptedException ignored) { }
    }

    public void handleButtonEnabled() {
        if(!checkEnabled.isSelected()){
            if(timerFixBugs.isRunning()){ timerFixBugs.stop();}
            if(timerPlay.isRunning()){ timerPlay.stop();}
            if(timerSitFast.isRunning()){ timerSitFast.stop();}
        }
    }

    public void handleButtonAutoPlay() {
        if(checkAutoPlay.isSelected()){
            if(!timerPlay.isRunning()){ timerPlay.start();}
            if(!timerFixBugs.isRunning()) { timerFixBugs.start(); }
            sendToClient(new HPacket("{in:Chat}{i:" + habboIndex + "}{s:\"Certifica que ta marcando essa opção quando já está sentado no banco do jogo, senão você não consegue se mexer!\"}{i:0}{i:2}{i:0}{i:-1}"));
        }else {
            if(timerPlay.isRunning()){ timerPlay.stop();}
        }
    }

    public void handleButtonBugBanzai() {
        if(!checkBanzaiBug.isSelected()){
            sentCommand = false;
            isTeleporting = false;
            isSitted = true;
            inAColorTile = false;
            inABanzai = false;
        }
    }

}
