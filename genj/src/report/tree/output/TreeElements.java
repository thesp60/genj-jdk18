/**
 * Reports are Freeware Code Snippets
 *
 * This report is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package tree.output;

import tree.FamBox;
import tree.IndiBox;

/**
 * Interface for classes rendering tree elements.
 *
 * @author Przemek Wiech <pwiech@losthive.org>
 */
public interface TreeElements {
    /**
     * Outputs an individual box.
     * @param i  individual
     * @param x  x coordinate
     * @param y  y coordinate
     * @param gen generation number
     */
    public void drawIndiBox(IndiBox indibox, int x, int y, int gen);

    /**
     * Outputs a family box.
     * @param i  individual
     * @param x  x coordinate
     * @param y  y coordinate
     * @param gen generation number
     */
    public void drawFamBox(FamBox fambox, int x, int y, int gen);

    /**
     * Outputs a line.
     * @param x1 start x
     * @param y1 start y
     * @param x2 end x
     * @param y2 end y
     */
    public void drawLine(int x1, int y1, int x2, int y2);

    /**
     * Outputs a dashed line.
     * @param x1 start x
     * @param y1 start y
     * @param x2 end x
     * @param y2 end y
     */
    public void drawDashedLine(int x1, int y1, int x2, int y2);

    /**
     * Outputs the image header.
     * @param w family tree width in pixels
     * @param h family tree height in generation lines
     */
    public void header(int width, int height);

    /**
     * Outputs the image footer.
     */
    public void footer();

}