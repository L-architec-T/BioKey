import QtQuick
import QtQuick.Controls
import QtQuick.Controls.impl
import QtQuick.Layouts
import QtQuick.Controls.Material

import PCBioUnlock
import 'qrc:/ui/base'

Form {
    RowLayout {
        id: mainRowLayout
        anchors.fill: parent
        ColumnLayout {
            Layout.preferredWidth: parent.width / 3.5
            Layout.preferredHeight: devicesBox.height
            Layout.minimumHeight: devicesBox.height
            Layout.maximumHeight: devicesBox.height
            Layout.alignment: Qt.AlignTop
            Layout.rightMargin: 10
            spacing: 12
            GroupBox {
                title: QI18n.Get('service_module')
                Layout.fillWidth: true
                background: Rectangle {
                    color: '#141414'
                    radius: 12
                    border.color: '#3A3A3A'
                    border.width: 1
                }
                label: Label {
                    text: QI18n.Get('service_module').toUpperCase()
                    color: Material.accent
                    font.bold: true
                    font.pixelSize: 14
                    leftPadding: 14
                    topPadding: 6
                    bottomPadding: 6
                }
                ColumnLayout {
                    anchors.fill: parent
                    RowLayout {
                        spacing: 4
                        Layout.topMargin: -3
                        Label {
                            text: '%1:'.arg(QI18n.Get('status'))
                            color: 'white'
                        }
                        Label {
                            text: MainWindow.IsInstalled() ? QI18n.Get('installed') : QI18n.Get('not_installed')
                            color: MainWindow.IsInstalled() ? Material.accent : Material.foreground
                            font.bold: true
                        }
                    }
                    RowLayout {
                        spacing: 4
                        Layout.topMargin: -6
                        visible: MainWindow.IsInstalled()
                        Label {
                            text: 'Version: '
                            color: 'white'
                        }
                        Label {
                            text: MainWindow.GetInstalledVersion().split('-')[0]
                            color: Material.accent
                            font.bold: true
                        }
                    }
                }
            }
            Item { Layout.fillHeight: true }
            GroupBox {
                Layout.fillWidth: true
                background: Rectangle {
                    color: '#141414'
                    radius: 12
                    border.color: '#3A3A3A'
                    border.width: 1
                }
                ColumnLayout {
                    anchors.fill: parent
                    Button {
                        id: uninstallBtn
                        Material.roundedScale: Material.SmallScale
                        Layout.fillWidth: true
                        Layout.preferredHeight: 68
                        topPadding: 4
                        bottomPadding: 4
                        font.pixelSize: 24
                        icon.width: 44
                        icon.height: 44
                        icon.color: Material.accent
                        text: MainWindow.IsInstalled() ? QI18n.Get('uninstall') : QI18n.Get('install')
                        icon.source: MainWindow.IsInstalled() ? 'qrc:/res/icons/buttons/ic_uninstall.svg' : ''
                        onClicked: MainWindow.OnInstallClicked(window)
                        contentItem: RowLayout {
                            spacing: uninstallBtn.spacing
                            IconImage {
                                source: uninstallBtn.icon.source
                                color: uninstallBtn.icon.color
                                Layout.preferredWidth: uninstallBtn.icon.width
                                Layout.preferredHeight: uninstallBtn.icon.height
                            }
                            Label {
                                text: uninstallBtn.text
                                font: uninstallBtn.font
                                color: uninstallBtn.enabled ? uninstallBtn.Material.foreground : uninstallBtn.Material.hintTextColor
                            }
                            Item { Layout.fillWidth: true }
                        }
                    }
                    Button {
                        id: reinstallBtn
                    Material.roundedScale: Material.SmallScale
                    Layout.fillWidth: true
                    Layout.preferredHeight: 68
                    topPadding: 4
                    bottomPadding: 4
                    font.pixelSize: 24
                    icon.width: 44
                    icon.height: 44
                    icon.color: Material.accent
                    text: QI18n.Get('reinstall')
                    icon.source: 'qrc:/res/icons/buttons/ic_reinstall.svg'
                    enabled: MainWindow.IsInstalled()
                    onClicked: MainWindow.OnReinstallClicked(window)
                    contentItem: RowLayout {
                        spacing: reinstallBtn.spacing
                        IconImage {
                            source: reinstallBtn.icon.source
                            color: reinstallBtn.icon.color
                            Layout.preferredWidth: reinstallBtn.icon.width
                            Layout.preferredHeight: reinstallBtn.icon.height
                        }
                        Label {
                            text: reinstallBtn.text
                            font: reinstallBtn.font
                            color: reinstallBtn.enabled ? reinstallBtn.Material.foreground : reinstallBtn.Material.hintTextColor
                        }
                        Item { Layout.fillWidth: true }
                    }
                }
                Button {
                    id: settingsBtn
                    Material.roundedScale: Material.SmallScale
                    Layout.fillWidth: true
                    Layout.preferredHeight: 68
                    topPadding: 4
                    bottomPadding: 4
                    font.pixelSize: 24
                    icon.width: 44
                    icon.height: 44
                    icon.color: Material.accent
                    text: QI18n.Get('settings')
                    icon.source: 'qrc:/res/icons/buttons/ic_settings.svg'
                    enabled: MainWindow.IsInstalled()
                    onClicked: SettingsForm.Show(viewLoader)
                    contentItem: RowLayout {
                        spacing: settingsBtn.spacing
                        IconImage {
                            source: settingsBtn.icon.source
                            color: settingsBtn.icon.color
                            Layout.preferredWidth: settingsBtn.icon.width
                            Layout.preferredHeight: settingsBtn.icon.height
                        }
                        Label {
                            text: settingsBtn.text
                            font: settingsBtn.font
                            color: settingsBtn.enabled ? settingsBtn.Material.foreground : settingsBtn.Material.hintTextColor
                        }
                        Item { Layout.fillWidth: true }
                    }
                }
                }
            }
        }

        GroupBox {
            id: devicesBox
            title: QI18n.Get('devices')
            Layout.preferredWidth: parent.width / 1.5
            Layout.preferredHeight: parent.height - 55
            Layout.alignment: Qt.AlignRight | Qt.AlignTop
            background: Rectangle {
                color: '#141414'
                radius: 12
                border.color: '#3A3A3A'
                border.width: 1
            }
            label: Label {
                text: QI18n.Get('devices').toUpperCase()
                color: Material.accent
                font.bold: true
                font.pixelSize: 14
                leftPadding: 14
                topPadding: 6
                bottomPadding: 6
            }
            ColumnLayout {
                anchors.fill: parent
                RowLayout {
                    spacing: 8
                    Layout.bottomMargin: 6
                    Rectangle {
                        width: 10
                        height: 10
                        radius: 5
                        color: MainWindow.IsPaired() ? '#4CAF50' : '#757575'
                    }
                    Label {
                        text: '%1:'.arg(QI18n.Get('status'))
                        color: 'white'
                    }
                    Label {
                        text: MainWindow.IsPaired() ? QI18n.Get('paired') : QI18n.Get('not_paired')
                        color: MainWindow.IsPaired() ? '#4CAF50' : Material.foreground
                        font.bold: true
                    }
                }
                DevicesTableModel {
                    id: devicesTableModel
                }
                Rectangle {
                    Layout.fillWidth: true
                    implicitHeight: 44
                    radius: 8
                    color: '#1C1C1C'
                    border.color: '#3A3A3A'
                    border.width: 1
                    clip: true
                    HorizontalHeaderView {
                        anchors.fill: parent
                        id: devicesTableHeader
                        syncView: devicesTableView
                        model: [QI18n.Get('device_name'), QI18n.Get('user'), QI18n.Get('method')]
                        clip: true
                        delegate: Item {
                            implicitHeight: 44
                            Rectangle {
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.bottom: parent.bottom
                                width: 1
                                color: '#3A3A3A'
                                visible: index < 2
                            }
                            Label {
                                color: Material.foreground
                                font.bold: true
                                text: modelData
                                anchors.left: parent.left
                                anchors.leftMargin: 14
                                anchors.verticalCenter: parent.verticalCenter
                            }
                        }
                    }
                }
                TableView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    id: devicesTableView
                    boundsBehavior: Flickable.StopAtBounds
                    columnWidthProvider: function(column) { return devicesTableView.width / 3; }
                    model: devicesTableModel

                    property int selectedRow: -1
                    delegate: ItemDelegate {
                        highlighted: row === devicesTableView.selectedRow
                        leftPadding: 14
                        onClicked: {
                            devicesTableView.selectedRow = row
                            deviceRemoveBtn.enabled = row !== -1;
                            deviceTestUnlockBtn.enabled = row !== -1;
                        }
                        text: model.tableData
                    }
                }
                RowLayout {
                    Button {
                        Material.roundedScale: Material.SmallScale
                        Layout.fillWidth: true
                        Layout.rightMargin: 24
                        Layout.preferredHeight: 60
                        text: QI18n.Get('pair_device')
                        enabled: MainWindow.IsInstalled()
                        highlighted: true
                        onClicked: PairingForm.Show(viewLoader, window)
                    }
                    Button {
                        Material.roundedScale: Material.SmallScale
                        Layout.fillWidth: true
                        Layout.preferredWidth: 90
                        Layout.preferredHeight: 60
                        id: deviceRemoveBtn
                        text: QI18n.Get('remove_device')
                        enabled: false
                        onClicked: {
                            showConfirmMessage(QI18n.Get('confirm_remove_device'), function () {
                                let selDevice = devicesTableModel.get(devicesTableView.selectedRow);
                                MainWindow.OnRemoveDeviceClicked(viewLoader, selDevice[0]);
                            });
                        }
                    }
                    Button {
                        Material.roundedScale: Material.SmallScale
                        Layout.fillWidth: true
                        Layout.preferredWidth: 130
                        Layout.preferredHeight: 60
                        id: deviceTestUnlockBtn
                        text: QI18n.Get('unlock_test')
                        enabled: false
                        onClicked: {
                            let selDevice = devicesTableModel.get(devicesTableView.selectedRow);
                            let unlockWin = Qt.createComponent("qrc:/ui/UnlockTestWindow.qml").createObject(window, {deviceId: selDevice[0]});
                            unlockWin.show();
                        }
                    }
                }
            }
        }
    }
    ColumnLayout {
        anchors.top: mainRowLayout.top
        anchors.bottom: parent.bottom
        anchors.bottomMargin: -10
        width: parent.width
        height: 60
        ColumnLayout {
            Layout.minimumWidth: 100
            Layout.alignment: Qt.AlignBottom | Qt.AlignHCenter
            Item { Layout.preferredHeight: 4 }
            RowLayout {
                Button {
                    Material.roundedScale: Material.SmallScale
                    Layout.minimumWidth: 100
                    Layout.minimumHeight: 40
                    text: QI18n.Get('about')
                    onClicked: {
                        let aboutWin = Qt.createComponent("qrc:/ui/AboutWindow.qml").createObject(window);
                        aboutWin.show();
                    }
                }
                Button {
                    Material.roundedScale: Material.SmallScale
                    Layout.minimumWidth: 100
                    Layout.minimumHeight: 40
                    text: QI18n.Get('logs')
                    onClicked: {
                        let logsWin = Qt.createComponent("qrc:/ui/LogsWindow.qml").createObject(window);
                        logsWin.show();
                    }
                }
            }
        }
    }
}
