import { useEffect, useRef, useState } from 'react';

const replies = [
  'Sounds fun! Keep the memes rolling.',
  'That is peak family energy right there.',
  'LOL. I am saving that one to my virtual scrapbook.',
  'Certified classic. 10/10 would meme again.',
  'Plot twist: the dog is actually the mastermind.',
  'We need snacks for this conversation. Always snacks.',
];

const pickReply = () => replies[Math.floor(Math.random() * replies.length)];

const RugChat = () => {
  const [messages, setMessages] = useState([
    { id: 0, from: 'bot', text: 'Hey there! Tell me your funniest family plan and I will react.' },
  ]);
  const [input, setInput] = useState('');
  const [typing, setTyping] = useState(false);
  const listRef = useRef(null);

  useEffect(() => {
    if (!listRef.current) return;
    listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [messages, typing]);

  const handleSend = (evt) => {
    evt.preventDefault();
    const text = input.trim();
    if (!text) return;
    const userMsg = { id: Date.now(), from: 'you', text };
    setMessages((prev) => [...prev, userMsg]);
    setInput('');
    setTyping(true);
    setTimeout(() => {
      setMessages((prev) => [...prev, { id: Date.now() + 1, from: 'bot', text: pickReply() }]);
      setTyping(false);
    }, 500);
  };

  return (
    <div className="chat">
      <div className="chat-list" ref={listRef} aria-live="polite">
        {messages.map((msg) => (
          <div key={msg.id} className={`bubble ${msg.from === 'you' ? 'bubble-you' : 'bubble-bot'}`}>
            <span className="bubble-from">{msg.from === 'you' ? 'You' : 'RUG'}</span>
            <span>{msg.text}</span>
          </div>
        ))}
        {typing ? <div className="bubble bubble-bot">RUG is typing…</div> : null}
      </div>
      <form className="chat-input" onSubmit={handleSend}>
        <input
          type="text"
          placeholder="Share a family joke or plan..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
        />
        <button type="submit">Send</button>
      </form>
    </div>
  );
};

export default RugChat;
