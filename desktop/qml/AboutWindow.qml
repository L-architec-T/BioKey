import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import QtQuick.Window
import PCBioUnlock

ApplicationWindow {
    id: aboutWindow
    width: 800
    height: 600
    title: QI18n.Get('about')
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 25
        Label {
            text: 'BioKey'
            font.pointSize: 36
        }
        Label {
            text: '%1 %2'.arg(QI18n.Get('version')).arg(UpdaterWindow.GetAppVersion())
            font.pointSize: 16
        }
        Label {
            text: QI18n.Get('by_meis_apps')
        }
        Label {
            text: "<a href=\"https://meis-apps.com\">https://meis-apps.com</a>"
            onLinkActivated: function(link) { Qt.openUrlExternally(link); }
        }
        Label {
            Layout.topMargin: 10
            wrapMode: Text.WordWrap
            Layout.fillWidth: true
            text: QI18n.Get('contribution_credit')
            color: Material.accent
        }
        Label {
            Layout.topMargin: 15
            text: '%1:'.arg(QI18n.Get('licenses'))
        }
        ScrollView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            TextArea {
                readOnly: true
                text: MainWindow.GetLicenseText()
            }
        }
    }
}
