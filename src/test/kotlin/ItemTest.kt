import com.yoox.net.models.outbound.*
import org.junit.Assert.*
import org.junit.Test

class ItemTest {
    @Test
    fun availability() {
        val item = Item(
            Department(
                "women", "women"
            ),
            Brand(203, "MARNI"),
            Category(
                CategoryName("Coat", "Coats"),
                CategoryName("Coats and Jackets", "Coats and Jackets")
            ),
            listOf(
                Image(
                    "http://exampl.ecom/image.png",
                    "http://exampl.ecom/image_zoom.png"
                )
            ),
            "100% Virgin Wool",
            SaleLine("2268", "PLV_JUST IN"),
            listOf(
                Color(
                    51,
                    "41868153MO",
                    "Brick red",
                    "906058",
                    listOf(
                        Size(
                            3,
                            "8",
                            "UK",
                            1
                        ),
                        Size(
                            4,
                            "10",
                            "UK",
                            1
                        )
                    )
                ),
                Color(
                    52,
                    "41868153MP",
                    "Brown",
                    "906059",
                    listOf(
                        Size(
                            3,
                            "8",
                            "UK",
                            1
                        ),
                        Size(
                            4,
                            "10",
                            "UK",
                            2
                        )
                    )
                )
            ),
            Price(1051.00f, "GBP", "1,051.00"),
            Price(902.00f, "GBP", "902.00")
        )
        assertEquals(5, item.globalAvailability())
        assertEquals(2, item.colors[0].globalAvailability())
        assertEquals(3, item.colors[1].globalAvailability())
    }
}