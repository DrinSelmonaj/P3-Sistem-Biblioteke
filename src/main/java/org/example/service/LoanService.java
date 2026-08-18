package org.example.service;

import org.example.dao.BookDAO;
import org.example.dao.DVDDAO;
import org.example.dao.LoanDAO;
import org.example.dao.MemberDAO;
import org.example.dao.ReservationDAO;
import org.example.model.Book;
import org.example.model.DVD;
import org.example.model.LibraryItem;
import org.example.model.Loan;
import org.example.model.Member;
import org.example.model.Reservation;

import java.time.LocalDate;
import java.util.List;

public class LoanService {

    // Composition + Dependency Injection, i njejti pattern si te DAO-t —
    // LoanService "ka" akses tek DAO-t, nuk i krijon vete brenda klases.
    private final MemberDAO memberDAO;
    private final LoanDAO loanDAO;
    private final BookDAO bookDAO;
    private final DVDDAO dvdDAO;
    private final ReservationDAO reservationDAO;

    public LoanService(MemberDAO memberDAO, LoanDAO loanDAO, BookDAO bookDAO,
                       DVDDAO dvdDAO, ReservationDAO reservationDAO) {
        this.memberDAO = memberDAO;
        this.loanDAO = loanDAO;
        this.bookDAO = bookDAO;
        this.dvdDAO = dvdDAO;
        this.reservationDAO = reservationDAO;
    }

    // TODO (vendim i njohur, i pranuar me vetedije — shih diskutimin per Opsionin A):
    // save(loan) dhe update(item) me poshte jane 2 thirrje DB te veçanta, jo brenda
    // nje transaksioni te perbashket. Nese e dyta deshton (p.sh. humbje lidhjeje),
    // mbetet gjendje e pjesshme: huazimi eshte ruajtur, por item mbetet "available".
    // Refaktorizim i mundshem me vone (Connection e ndare mes DAO-ve / Unit of Work),
    // nese behet problem real i deshmuar — jo prioritet tani per sistem CLI single-user.
    public Loan borrowItem(String memberId, String itemId) {
        Member member = memberDAO.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Anetari me ID " + memberId + " nuk u gjet."));

        LibraryItem item = resolveItem(itemId);

        if (!item.isAvailable()) {
            throw new IllegalStateException("Artikulli " + itemId + " s'eshte i disponueshem per huazim.");
        }

        if (member.isBlocked()) {
            throw new IllegalStateException("Anetari " + memberId + " ka gjoba te papaguara — huazimi eshte i bllokuar.");
        }

        // Perdorim loanDAO.findActiveByMember() ne vend te member.canBorrowMore(),
        // sepse currentLoans ne Member s'eshte i populluar nga DB (shih diskutimin
        // per varesine e kryqezuar Member<->Loan). Kjo eshte burimi i vertete i te dhenave.
        int activeLoans = loanDAO.findActiveByMember(memberId).size();
        if (activeLoans >= Member.getMaxLoans()) {
            throw new IllegalStateException(
                    "Anetari " + memberId + " ka arritur kufirin prej " + Member.getMaxLoans() + " huazimesh aktive.");
        }

        Loan loan = new Loan(member, item, LocalDate.now());
        loanDAO.save(loan);

        item.setAvailable(false);
        persistAvailability(item);

        return loan;
    }

    public void returnItem(int loanId) {
        Loan loan = loanDAO.findById(loanId)
                .orElseThrow(() -> new IllegalArgumentException("Huazimi me ID " + loanId + " nuk u gjet."));

        if (loan.isReturned()) {
            throw new IllegalStateException("Huazimi " + loanId + " eshte kthyer tashme.");
        }

        LocalDate returnDate = LocalDate.now();
        loanDAO.markReturned(loanId, returnDate);
        loan.markReturned(returnDate);

        LibraryItem item = loan.getItem();

        // Aktivizim FIFO: nese ka rezervime ne pritje per kete artikull, radha
        // (e llogaritur "on the fly" nga ReservationDAO) percakton kush eshte i radhes.
        // E shenojme fulfilled dhe artikulli MBETET i parezervueshem (available=false)
        // per kete anetar — sistemi s'ka gjendje te vecante "e mbajtur per marrje".
        List<Reservation> queue = reservationDAO.findQueueForItem(item.getId());

        if (queue.isEmpty()) {
            item.setAvailable(true);
        } else {
            Reservation next = queue.get(0);
            reservationDAO.markFulfilled(next.getId());
            item.setAvailable(false);
        }

        persistAvailability(item);
    }

    // Provo Book, pastaj DVD — supozon hapesira ID te veçanta (p.sh. B001/D001).
    // Njesoj konceptualisht si resolveItem() ne LoanDAOImpl, por ketu s'ka akses
    // direkt te library_items.item_type (do te ishte SQL ne service layer).
    private LibraryItem resolveItem(String itemId) {
        return bookDAO.findById(itemId)
                .map(b -> (LibraryItem) b)
                .or(() -> dvdDAO.findById(itemId).map(d -> (LibraryItem) d))
                .orElseThrow(() -> new IllegalArgumentException("Artikulli me ID " + itemId + " nuk u gjet."));
    }

    // Polimorfizem: duhet te dime nese item eshte Book apo DVD per te thirrur
    // update() te DAO-n e sakte (BookDAO/DVDDAO s'ndajne nje update() te perbashket).
    private void persistAvailability(LibraryItem item) {
        if (item instanceof Book book) {
            bookDAO.update(book);
        } else if (item instanceof DVD dvd) {
            dvdDAO.update(dvd);
        } else {
            throw new IllegalStateException("Lloj i panjohur i LibraryItem: " + item.getClass());
        }
    }
}