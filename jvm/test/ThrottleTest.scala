
import java.awt.FlowLayout
import java.awt.event.{ActionEvent, ActionListener}
import javax.swing.{JButton, JFrame, JLabel, JPanel}

import redosignals.Source

object ThrottleTest extends App with redosignals.Observer {
  {
    val input = new Source[Char]('A')
    val accepted = new Source[Int](0)
    val output = input.throttleBy(accepted)

    output foreach { o =>
      println(s"Output changed to $o")
    }

    val win = new JFrame()
    val panel = new JPanel()
    panel.setLayout(new FlowLayout)
    val incButton = new JButton("Inc")
    incButton.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        input() = (input.now + 1).toChar
      }
    })
    panel.add(incButton)
    val inputLabel = new JLabel("")
    input foreach (i => inputLabel.setText(i.toString))
    panel.add(inputLabel)
    val acceptButton = new JButton("Accept")
    acceptButton.addActionListener(new ActionListener {
      override def actionPerformed(e: ActionEvent): Unit = {
        accepted() = output.now._2
      }
    })
    panel.add(acceptButton)
    val outputLabel = new JLabel("")
    output foreach (o => outputLabel.setText(o.toString))
    panel.add(outputLabel)
    win.add(panel)
    win.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE)
    win.pack()
    win.setLocationRelativeTo(null)
    win.setVisible(true)
  }
}
