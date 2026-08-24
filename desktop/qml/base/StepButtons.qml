import QtQuick
import QtQuick.Controls
import QtQuick.Controls.Material
import QtQuick.Layouts
import PCBioUnlock

Rectangle {
    Layout.fillWidth: true
    Layout.alignment: Qt.AlignBottom
    Layout.minimumHeight: 60
    id: stepButtonsRect
    color: window.color

    property alias backBtn: stepButtonBack
    property alias nextBtn: stepButtonNext

    property string backTitle: QI18n.Get('back')
    property string nextTitle: QI18n.Get('next')
    signal backClicked()
    signal nextClicked()

    RowLayout {
        anchors.fill: parent
        Button {
            Material.roundedScale: Material.SmallScale
            Layout.minimumWidth: 100
            Layout.minimumHeight: 50
            id: stepButtonBack
            text: stepButtonsRect.backTitle
            onClicked: stepButtonsRect.backClicked()
        }
        Button {
            Material.roundedScale: Material.SmallScale
            Layout.minimumWidth: 100
            Layout.minimumHeight: 50
            Layout.alignment: Qt.AlignRight
            id: stepButtonNext
            text: stepButtonsRect.nextTitle
            onClicked: stepButtonsRect.nextClicked()
        }
    }
}
